/*
 * Copyright (c) 2016 KKurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.service;

import android.util.Log;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.common.HashMapMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.BuildConfig;

/**
 * {@link BayeuxClient} implementation for the Squeezer App.
 * <p>
 * This is responsible for logging.
 */
class SqueezerBayeuxClient extends BayeuxClient {
    private static final String TAG = SqueezerBayeuxClient.class.getSimpleName();
    private static final boolean LOG_JSON_PRETTY_PRINT = false;

    SqueezerBayeuxClient(String url, ClientTransport transport, ClientTransport... transports) {
        super(url, transport, transports);
    }

    @Override
    public void onSending(List<? extends Message> messages) {
        super.onSending(messages);
        for (Message message : messages) {
            if (BuildConfig.DEBUG) {
                logMessage("SEND", message);
            }
        }
    }

    @Override
    public void onMessages(List<Message.Mutable> messages) {
        super.onMessages(messages);
        for (Message message : messages) {
            if (BuildConfig.DEBUG) {
                logMessage("RECV", message);
            }
        }
    }

    @Override
    public void onFailure(Throwable failure, List<? extends Message> messages) {
        super.onFailure(failure, messages);
        for (Message message : messages) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "FAIL: " + message.getJSON(), failure);
            }
        }
        if (failure instanceof IOException) {
            rehandshake();
        }
    }

    public void rehandshake() {
        HashMapMessage message = new HashMapMessage();
        message.setId(newMessageId());
        message.setSuccessful(false);
        message.setChannel(Channel.META_HANDSHAKE);
        message.getAdvice(true).put(Message.RECONNECT_FIELD, Message.RECONNECT_HANDSHAKE_VALUE);
        message.setClientId(getId());
        processHandshake(message);
    }

    private void logMessage(String s, Message message) {
        if (LOG_JSON_PRETTY_PRINT) {
            StringBuilder sb = new StringBuilder();
            json(sb, 0, null, message);
            // To avoid having long messages truncated
            while (sb.length() > 4000) {
                Log.v(TAG, s + ":\n" + sb.substring(0, 4000));
                sb.delete(0, 4000);
            }
            Log.v(TAG, s + ":\n" + sb.toString());
        } else {
            Log.v(TAG, s + ": " + message.getJSON());
        }
    }

    private void json(StringBuilder sb, int indent, String key, Object object) {
        if (indent > 0) sb.append(String.format("%" + indent*2 + "s", ""));
        if (key != null) {
            sb.append(key);
        }
        if (object == null) {
            sb.append("null\n");
        } else if (object.getClass().isArray()) {
            Object[] arr = (Object[]) object;
            sb.append("[\n");
            for (Object o : arr) {
                json(sb, indent+1, null, o);
            }
            json(sb, indent, null, "]");
        } else if (object instanceof List) {
            List<?> list = (List<?>) object;
            sb.append("[\n");
            for (Object o : list) {
                json(sb, indent+1, null, o);
            }
            json(sb, indent, null, "]");
        } else if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            sb.append("{\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                json(sb, indent+1, entry.getKey() + ": ", entry.getValue());
            }
            json(sb, indent, null, "}");
        } else {
            sb.append(object.toString()).append("\n");
        }
    }
}
