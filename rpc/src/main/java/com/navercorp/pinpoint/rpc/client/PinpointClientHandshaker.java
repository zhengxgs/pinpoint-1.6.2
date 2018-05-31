/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.rpc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.cluster.Role;
import com.navercorp.pinpoint.rpc.util.*;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.navercorp.pinpoint.rpc.control.ProtocolException;
import com.navercorp.pinpoint.rpc.packet.ControlHandshakePacket;
import com.navercorp.pinpoint.rpc.packet.ControlHandshakeResponsePacket;
import com.navercorp.pinpoint.rpc.packet.HandshakeResponseCode;

public class PinpointClientHandshaker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ChannelFutureListener handShakeFailFutureListener = new WriteFailFutureListener(this.logger, "HandShakePacket write fail.", "HandShakePacket write success.");
    
    private static final int STATE_INIT = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_FINISHED = 2;
    // STATE_INIT -> STATE_STARTED -> STATE_COMPLETED
    // STATE_INIT -> STATE_STARTED -> STATE_ABORTED
    private final AtomicInteger state; 
    
    private final AtomicInteger handshakeCount;

    private final Timer handshakerTimer;
    private final int retryInterval;
    private final int maxHandshakeCount;
    
    private final Object lock = new Object();
    private final AtomicReference<HandshakeResponseCode> handshakeResult = new AtomicReference<HandshakeResponseCode>(null);
    private final AtomicReference<ClusterOption> clusterOption = new AtomicReference<ClusterOption>(null);
    
    private String simpleName;
    
    public PinpointClientHandshaker(Timer handshakerTimer, int retryInterval, int maxHandshakeCount) {
        AssertUtils.assertNotNull(handshakerTimer, "handshakerTimer may not be null.");
        AssertUtils.assertTrue(retryInterval > 0, "retryInterval must greater than zero.");
        AssertUtils.assertTrue(maxHandshakeCount > 0, "maxHandshakeCount must greater than zero.");
        
        this.state = new AtomicInteger(STATE_INIT);
        this.handshakerTimer = handshakerTimer;
        this.retryInterval = retryInterval;
        this.maxHandshakeCount = maxHandshakeCount;
        
        this.handshakeCount = new AtomicInteger(0);
    }
 
    public void handshakeStart(Channel channel, Map<String, Object> handshakeData) {
        logger.info("{} handshakeStart() started. channel:{}", simpleClassNameAndHashCodeString(), channel);
        
        if (channel == null) {
            logger.warn("{} handshakeStart() failed. caused:channel may not be null.", simpleClassNameAndHashCodeString());
            return;
        }
        
        if (!channel.isConnected()) {
            logger.warn("{} handshakeStart() failed. caused:channel is not connected.", simpleClassNameAndHashCodeString());
            return;
        }

        if (!state.compareAndSet(STATE_INIT, STATE_STARTED)) {
            logger.warn("{} handshakeStart() failed. caused:unexpected state.", simpleClassNameAndHashCodeString());
            return;
        }
        
        HandshakeJob handshakeJob = null;
        try {
            handshakeJob = createHandshakeJob(channel, handshakeData);
        } catch (Exception e) {
            logger.warn("{} create HandshakeJob failed. caused:{}", simpleClassNameAndHashCodeString(), e.getMessage(), e);
        }

        if (handshakeJob == null) {
            logger.warn("{} handshakeStart() failed. caused:handshakeJob may not be null.", simpleClassNameAndHashCodeString());
            handshakeAbort();
            return;
        }

        handshake(handshakeJob);
        reserveHandshake(handshakeJob);
        logger.info("{} handshakeStart() completed. channel:{}, data:{}", simpleClassNameAndHashCodeString(), channel, handshakeData);
    }

    private HandshakeJob createHandshakeJob(Channel channel, Map<String, Object> handshakeData) throws ProtocolException {
        byte[] payload = ControlMessageEncodingUtils.encode(handshakeData);
        ControlHandshakePacket handshakePacket = new ControlHandshakePacket(payload);

        HandshakeJob handshakeJob = new HandshakeJob(channel, handshakePacket);
        return handshakeJob;
    }
    
    private void handshake(HandshakeJob handshakeJob) {
        handshakeCount.incrementAndGet();
        
        Channel channel = handshakeJob.getChannel();
        ControlHandshakePacket packet = handshakeJob.getHandshakePacket();
        
        logger.info("{} do handshake({}/{}). channel:{}.", simpleClassNameAndHashCodeString(), handshakeCount.get(), maxHandshakeCount, channel);
        final ChannelFuture future = channel.write(packet);

        future.addListener(handShakeFailFutureListener);
    }

    private void reserveHandshake(HandshakeJob handshake) {
        if (handshakeCount.get() >= maxHandshakeCount) {
            logger.warn("{} reserveHandshake() failed. caused:Retry count is over({}/{}).", simpleClassNameAndHashCodeString(), handshakeCount.get(), maxHandshakeCount);
            handshakeAbort();
            return;
        }

        logger.debug("{} reserveHandshake() started.", simpleClassNameAndHashCodeString());
        this.handshakerTimer.newTimeout(handshake, retryInterval, TimeUnit.MILLISECONDS);
    }
    
    public boolean handshakeComplete(ControlHandshakeResponsePacket responsePacket) {
        logger.info("{} handshakeComplete() started. responsePacket:{}", simpleClassNameAndHashCodeString(), responsePacket);

        synchronized (lock) {
            if (!this.state.compareAndSet(STATE_STARTED, STATE_FINISHED)) {
                // state can be 0 or 2.
                logger.info("{} handshakeComplete() failed. caused:unexpected state.", simpleClassNameAndHashCodeString());
                this.state.set(STATE_FINISHED);
                return false;
            }

            Map handshakeResponse = decode(responsePacket);

            HandshakeResponseCode code = getResponseCode(handshakeResponse);
            handshakeResult.compareAndSet(null, code);

            ClusterOption clusterOption = getClusterOption(handshakeResponse);
            this.clusterOption.compareAndSet(null, clusterOption);

            logger.info("{} handshakeComplete() completed. handshake-response:{}.", simpleClassNameAndHashCodeString(), handshakeResponse);
            return true;
        }
    }

    private Map decode(ControlHandshakeResponsePacket message) {
        byte[] payload = message.getPayload();
        if (payload == null) {
            return Collections.EMPTY_MAP;
        }

        try {
            Map result = (Map) ControlMessageEncodingUtils.decode(payload);
            return result;
        } catch (ProtocolException e) {

        }

        return Collections.EMPTY_MAP;
    }

    private HandshakeResponseCode getResponseCode(Map handshakeResponse) {
        if (handshakeResponse == Collections.EMPTY_MAP) {
            return HandshakeResponseCode.PROTOCOL_ERROR;
        }

        int code = MapUtils.getInteger(handshakeResponse, ControlHandshakeResponsePacket.CODE, -1);
        int subCode = MapUtils.getInteger(handshakeResponse, ControlHandshakeResponsePacket.SUB_CODE, -1);

        return HandshakeResponseCode.getValue(code, subCode);
    }

    private ClusterOption getClusterOption(Map handshakeResponse) {
        if (handshakeResponse == Collections.EMPTY_MAP) {
            return ClusterOption.DISABLE_CLUSTER_OPTION;
        }

        Map cluster = (Map) handshakeResponse.get(ControlHandshakeResponsePacket.CLUSTER);
        if (cluster == null) {
            return ClusterOption.DISABLE_CLUSTER_OPTION;
        }

        String id = MapUtils.getString(cluster, "id", "");
        List<Role> roles = getRoles((List) cluster.get("roles"));

        if (StringUtils.isEmpty(id)) {
            return ClusterOption.DISABLE_CLUSTER_OPTION;
        } else {
            return new ClusterOption(true, id, roles);
        }
    }

    private List<Role> getRoles(List roleNames) {
        List<Role> roles = new ArrayList<Role>();
        for (Object roleName : roleNames) {
            if (roleName instanceof String && !StringUtils.isEmpty((String) roleName)) {
                roles.add(Role.getValue((String) roleName));
            }
        }
        return roles;
    }

    public HandshakeResponseCode getHandshakeResult() {
        return handshakeResult.get();
    }

    public ClusterOption getClusterOption() {
        return clusterOption.get();
    }

    public void handshakeAbort() {
        logger.info("{} handshakeAbort() started.", simpleClassNameAndHashCodeString());

        if (!state.compareAndSet(STATE_STARTED, STATE_FINISHED)) {
            // state can be 0 or 2.
            logger.info("{} unexpected state", simpleClassNameAndHashCodeString());
            this.state.set(STATE_FINISHED);
            return;
        }
        logger.info("{} handshakeAbort() completed.", simpleClassNameAndHashCodeString());
    }
    
    public boolean isRun() {
        int currentState = currentState();
        return isRun(currentState);
    }
    
    private boolean isRun(int currentState) {
        if (currentState == STATE_STARTED) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isFinished() {
        int currentState = currentState();
        return isFinished(currentState);
    }

    private boolean isFinished(int currentState) {
        return currentState == STATE_FINISHED;
    }

    private int currentState() {
        synchronized (lock) {
            return this.state.get();
        }
    }
    
    private String simpleClassNameAndHashCodeString() {
        if (simpleName == null) {
            simpleName = ClassUtils.simpleClassNameAndHashCodeString(this);
        }
        
        return simpleName;
    }
    
    private class HandshakeJob implements TimerTask {

        private final Channel channel;
        private final ControlHandshakePacket handshakePacket;

        public HandshakeJob(Channel channel, ControlHandshakePacket handshakePacket) {
            this.channel = channel;
            this.handshakePacket = handshakePacket;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            logger.debug("{} HandshakeJob started.", simpleClassNameAndHashCodeString());

            if (timeout.isCancelled()) {
                reserveHandshake(this);
                return;
            }

            int currentState = currentState();
            
            if (isRun(currentState)) {
                handshake(this);
                reserveHandshake(this);
            } else if (isFinished(currentState)) {
                logger.info("{} HandshakeJob completed.", simpleClassNameAndHashCodeString());
            } else {
                logger.warn("{} HandshakeJob will be stop. caused:unexpected state.", simpleClassNameAndHashCodeString());
            }
        }

        public Channel getChannel() {
            return channel;
        }

        public ControlHandshakePacket getHandshakePacket() {
            return handshakePacket;
        }
    }

}
