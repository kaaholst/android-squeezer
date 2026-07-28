package uk.org.ngo.squeezer.service;

import android.util.Log;

import androidx.annotation.NonNull;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.client.transport.HttpClientTransport;
import org.cometd.client.transport.MessageClientTransport;
import org.cometd.client.transport.TransportListener;
import org.cometd.common.TransportException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;

import java.io.EOFException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;


public class HttpStreamingTransport extends HttpClientTransport implements MessageClientTransport {
    public static final String NAME = "streaming";
    public static final String PREFIX = "http-streaming.json";
    public static final String MAX_BUFFER_SIZE_OPTION = "maxBufferSize";
    private static final String TAG = HttpStreamingTransport.class.getSimpleName();

    private ScheduledExecutorService _scheduler;
    private boolean _shutdownScheduler;

    private final Delegate _delegate;
    private TransportListener _listener;

    private final HttpClient _httpClient;
    private final List<Request> _requests = new ArrayList<>();
    private volatile boolean _aborted;
    private volatile int _maxBufferSize;

    public HttpStreamingTransport(String url, Map<String, Object> options) throws Exception {
        super(NAME, url, options);

        _httpClient = new HttpClient();
        _httpClient.start();

        _delegate = new Delegate();
        setOptionPrefix(PREFIX);
    }

    @Override
    public void setMessageTransportListener(TransportListener listener) {
        _listener = listener;
    }

    @Override
    public boolean accept(String bayeuxVersion) {
        return true;
    }

    @Override
    public void init() {
        super.init();

        _aborted = false;

        long defaultMaxNetworkDelay = _httpClient.getIdleTimeout();
        if (defaultMaxNetworkDelay <= 0)
            defaultMaxNetworkDelay = 10000;
        setMaxNetworkDelay(defaultMaxNetworkDelay);

        _maxBufferSize = getOption(MAX_BUFFER_SIZE_OPTION, 1024 * 1024);

        if (_scheduler == null) {
            _shutdownScheduler = true;
            int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
            ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(threads);
            scheduler.setRemoveOnCancelPolicy(true);
            _scheduler = scheduler;
        }

    }

    @Override
    public void abort() {
        List<Request> requests;
        synchronized (this) {
            _aborted = true;
            requests = new ArrayList<>(_requests);
            _requests.clear();
        }
        for (Request request : requests) {
            request.abort(new Exception("Transport " + this + " aborted"));
        }
        shutdownScheduler();
        _delegate.disconnect("Aborted");
    }

    @Override
    public void terminate()
    {
        shutdownScheduler();
        super.terminate();
    }

    private void shutdownScheduler()
    {
        if (_shutdownScheduler)
        {
            _shutdownScheduler = false;
            _scheduler.shutdownNow();
            _scheduler = null;
        }
    }

    @Override
    public void send(final TransportListener listener, final List<Message.Mutable> messages) {
        List<Message.Mutable> transportMessages = new ArrayList<>();
        for (Message.Mutable message : messages) {
            String channel = message.getChannel();

            if (Channel.META_CONNECT.equals(channel)) {
                // The CometD library reschedules connect (channel META_CONNECT) messages to keep the connection
                // alive, but SN and LMS sees this as a connection request, and instead relies on an active
                // subscribe query (serverstatus or playerstatus) to provide keep-alive messages from the server
                // to the client.
                if (!_delegate.isConnected()) {
                    _delegate.connect(listener, message);
                } else {
                    Log.v(TAG, "Attempt to resend connect message, but we refuse that");
                }
            } else {
                if (Channel.META_HANDSHAKE.equals(channel)) {
                    // Make sure we get a new client id if this is a reconnect / rehandshake
                    if (message.getClientId() != null) {
                        Log.v(TAG, "Reset client id");
                        message.setClientId(null);
                    }

                    if (_delegate.isConnected()) {
                        _delegate.disconnect("Disconnect to prepare for a new handshake");
                    }
                }
                transportMessages.add(message);
            }
        }

        if (!transportMessages.isEmpty()) transportSend(listener, transportMessages);
    }

    private void transportSend(final TransportListener listener, final List<Message.Mutable> requestMessages) {
        final Request request = buildRequest(requestMessages);

        // TODO maybe add this to build request and use requests property instead of connectExchange in delegate
        synchronized (this) {
            if (_aborted) throw new IllegalStateException("Aborted");
            _requests.add(request);
        }

        long maxNetworkDelay = getMaxNetworkDelay();

        // Set the idle timeout for this request larger than the total timeout
        // so there are no races between the two timeouts
        request.idleTimeout(maxNetworkDelay * 2, TimeUnit.MILLISECONDS);
        request.timeout(maxNetworkDelay, TimeUnit.MILLISECONDS);
        request.send(new BufferingResponseListener(_maxBufferSize) {
            @Override
            public void onComplete(Result result) {
                synchronized (HttpStreamingTransport.this) {
                    _requests.remove(result.getRequest());
                }

                if (result.isFailed()) {
                    listener.onFailure(result.getFailure(), requestMessages);
                    return;
                }

                Response response = result.getResponse();
                int status = response.getStatus();
                if (status == HttpStatus.OK_200) {
                    String content = getContentAsString();
                    if (content != null && !content.isEmpty()) {
                        try {
                            List<Message.Mutable> responseMessages = parseMessages(content);
                            //Log.v(TAG, "Received messages " + content);
                            for (Message.Mutable message : responseMessages) {
                                // LMS echoes the data field in the publish response for messages to the
                                // slim/unsubscribe channel.
                                // This causes the comet libraries to decide the message is not a publish response.
                                // We remove the data field for such messages, to have them correctly recognized
                                // as publish responses.
                                if (message.getChannel() != null && message.getChannel().startsWith("/slim/")) {
                                    message.remove(Message.DATA_FIELD);
                                }

                                if (message.isMeta() && message.isSuccessful()) {
                                    Map<String, Object> advice = message.getAdvice();
                                    if (advice != null) {
                                        Log.v(TAG, message.getChannel() + " advice: " + advice);

                                        // Make sure interval is zero so we can send connect and rehandshake message
                                        // immediately.
                                        advice.put(Message.INTERVAL_FIELD, 0);
                                    }
                                }

                                if (message.isSuccessful()) {
                                    if (Channel.META_DISCONNECT.equals(message.getChannel())) {
                                        _delegate.disconnect("Disconnect");
                                    }
                                } else {
                                    // LMS does not put ID on all replies. In this case we look for a request with the same
                                    // channel as this response, and use the id from that request.
                                    if (message.isPublishReply() && message.getId() == null) {
                                        for (Message.Mutable requestMessage : requestMessages) {
                                            if (requestMessage.getChannel().equals(message.getChannel())) {
                                                message.setId(requestMessage.getId());
                                            }
                                        }
                                    }
                                }

                                if (Channel.META_SUBSCRIBE.equals(message.getChannel()) && message.get(Message.SUBSCRIPTION_FIELD) == null) {
                                    // The cometd library expects the subscription field to be echoed by the server.
                                    // LMS doesn't always do that. E.g. seen for failing messages.
                                    // In this case we look for a request with the same channel as this response, and
                                    // use the subscription field from that request.
                                    for (Message.Mutable requestMessage : requestMessages) {
                                        if (requestMessage.getChannel().equals(message.getChannel())) {
                                            message.put(Message.SUBSCRIPTION_FIELD, requestMessage.get(Message.SUBSCRIPTION_FIELD));
                                        }
                                    }
                                }
                            }
                            listener.onMessages(responseMessages);
                        } catch (ParseException x) {
                            listener.onFailure(x, requestMessages);
                        }
                    } else {
                        Map<String, Object> failure = new HashMap<>(2);
                        // Convert the 200 into 204 (no content)
                        failure.put("httpCode", 204);
                        TransportException x = new TransportException(failure);
                        listener.onFailure(x, requestMessages);
                    }
                } else {
                    Map<String, Object> failure = new HashMap<>(2);
                    failure.put("httpCode", status);
                    TransportException x = new TransportException(failure);
                    listener.onFailure(x, requestMessages);
                }
            }
        });
    }

    private Request buildRequest(List<Message.Mutable> messages) {
        final URI uri = URI.create(getURL());
        final Request request = _httpClient.POST(uri);
        request.header(HttpHeader.CONTENT_TYPE.asString(), "text/json;charset=UTF-8");

        String content = generateJSON(messages);
        //Log.v(TAG,"Sending messages " + content);
        request.content(new StringContentProvider(content));

        customize(request);

        request.listener(new Request.Listener.Adapter() {
            @Override
            public void onHeaders(Request request) {
                _listener.onSending(messages);
            }
        });

        return request;
    }

    private class Delegate {
        private final AtomicReference<Request> request = new AtomicReference<>();
        private final AtomicReference<Exchange> connectExchange = new AtomicReference<>();
        private final StringBuilder unprocessed = new StringBuilder();

        private final Response.Listener.Adapter streamingListener = new Response.Listener.Adapter() {
            @Override
            public void onContent(Response response, ByteBuffer content) {
                int remaining = content.remaining();
                if (remaining > 0) {
                    byte[] bytes = new byte[remaining];
                    content.get(bytes);
                    onData(new String(bytes, StandardCharsets.UTF_8));
                } else {
                    Log.w(TAG, MessageFormat.format("Queuing skipped, empty content: {0}", content));
                }
            }

            @Override
            public void onSuccess(Response response) {
                if (isConnected()) Log.i(TAG, MessageFormat.format("onSuccess({0})", response));
            }

            @Override
            public void onFailure(Response response, Throwable failure) {
                if (isConnected()) fail(failure, MessageFormat.format("onFailure({0})", response));
            }

            @Override
            public void onComplete(Result result) {
                if (isConnected()) disconnect(MessageFormat.format("onComplete({0})", result));
            }
        };

        private boolean isConnected() {
            return request.get() != null;
        }

        public void connect(final TransportListener listener, final Message.Mutable message) {
            Log.v(TAG, "Connect delegate");
            Request request = buildRequest(List.of(message));
            request.timeout(-1, TimeUnit.MILLISECONDS);
            request.idleTimeout(CometClient.SERVER_STATUS_TIMEOUT, TimeUnit.MILLISECONDS);
            registerMessage(message, listener);
            request.send(streamingListener);
            this.request.set(request);
        }

        private void disconnect(String reason) {
            Request request = this.request.getAndSet(null);
            if (request != null) {
                Log.v(TAG, "Disconnect delegate, reason: " + reason);
                request.abort(new EOFException(reason));
            }
        }

        private void fail(Throwable failure, String reason) {
            disconnect(reason);
            Exchange exchange = connectExchange.get();
            if (exchange != null) {
                Message.Mutable message = exchange.message;
                if (deregisterMessage(message) == exchange) {
                    exchange.listener.onFailure(failure, List.of(message));
                }
            } else {
                _listener.onFailure(failure, List.of());
            }
        }

        private void registerMessage(final Message.Mutable message, final TransportListener listener) {
            synchronized (this) {
                // Schedule a task to expire if the maxNetworkDelay elapses
                long maxNetworkDelay = getMaxNetworkDelay();
                ScheduledFuture<?> task = _scheduler.schedule(() -> fail(new TimeoutException(), "Expired"), maxNetworkDelay, TimeUnit.MILLISECONDS);

                Exchange exchange = new Exchange(message, listener, task);
                //Log.d(TAG, "Registering " + exchange);
                connectExchange.set(exchange);
            }
        }

        private Exchange deregisterMessage(Message.Mutable message) {
            Exchange exchange = Channel.META_CONNECT.equals(message.getChannel()) ? connectExchange.get() : null;
            //Log.d(TAG, "Deregistering " + exchange + " for message " + message);
            if (exchange != null) exchange.task.cancel(false);

            return exchange;
        }

        private void onData(String data) {
//            if (data.length() > 68)
//                Log.v(TAG,MessageFormat.format("onData: {0} ... {1}", data.substring(0, 32), data.substring(data.length()-32)));
//            else
//                Log.v(TAG,MessageFormat.format("onData: {0}", data));
            unprocessed.append(data);
            if (unprocessed.length() >= 2 && unprocessed.lastIndexOf("}]") == unprocessed.length()-2) {
                try {
                    //Log.v(TAG, MessageFormat.format("Received messages[{0}]: {1}", unprocessed.length(), unprocessed.toString()));
                    List<String> split = splitMessageArrays(unprocessed.toString());
                    List<List<Message.Mutable>> messagesLists = new ArrayList<>(split.size());
                    for (String s : split) messagesLists.add(parseMessages(s));
                    unprocessed.setLength(0);
                    for (List<Message.Mutable> messages : messagesLists) onMessages(messages);
                } catch (ParseException x) {
                    Log.i(TAG, "Incomplete response, wait for next chunk");
                }
            }
        }

        private void onMessages(List<Message.Mutable> messages) {
            for (Message.Mutable message : messages) {
                //Log.v(TAG,MessageFormat.format("Received message: {0}", message));
                if (isReply(message)) {
                    Exchange exchange = deregisterMessage(message);
                    if (exchange != null) {
                        exchange.listener.onMessages(List.of(message));
                    } else if (message.containsKey("error")) {
                        fail(null, "Received error: " +  message);
                        _listener.onFailure(null, List.of(message));
                    } else {
                        // If the exchange is missing, then the message has expired, and we do not notify
                        Log.d(TAG, "Could not find request for reply " +  message);
                    }
                } else {
                    _listener.onMessages(List.of(message));
                }
            }
        }

        private List<String> splitMessageArrays(String s) {
            List<String> split = new ArrayList<>();
            int p, p0 = 0;
            while ((p = s.indexOf("][", p0)) >= 0) {
                split.add(s.substring(p0, p+1));
                p0 = p+1;
            }
            if (p0 < s.length()) split.add(s.substring(p0));
            return split;
        }
    }

    private static class Exchange {
        private final Message.Mutable message;
        private final TransportListener listener;
        private final ScheduledFuture<?> task;

        public Exchange(Message.Mutable message, TransportListener listener, ScheduledFuture<?> task) {
            this.message = message;
            this.listener = listener;
            this.task = task;
        }

        @NonNull
        @Override
        public String toString() {
            return getClass().getSimpleName() + " " + message;
        }
    }

    private static boolean isReply(Message message) {
        return message.isMeta() || message.isPublishReply();
    }


    protected void customize(Request request) {
    }

}
