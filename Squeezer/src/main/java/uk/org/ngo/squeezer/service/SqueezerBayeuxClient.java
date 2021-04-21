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

import uk.org.ngo.squeezer.BuildConfig;

/**
 * {@link BayeuxClient} implementation for the Squeezer App.
 * <p>
 * This is responsible for logging.
 */
class SqueezerBayeuxClient extends BayeuxClient {
    private static final String TAG = SqueezerBayeuxClient.class.getSimpleName();

    SqueezerBayeuxClient(String url, ClientTransport transport, ClientTransport... transports) {
        super(url, transport, transports);
    }

    @Override
    public void onSending(List<? extends Message> messages) {
        super.onSending(messages);
        for (Message message : messages) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "SEND: " + message.getJSON());
            }
        }
    }

    @Override
    public void onMessages(List<Message.Mutable> messages) {
        super.onMessages(messages);
        for (Message message : messages) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "RECV: " + message.getJSON());
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
}
