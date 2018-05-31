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

package com.navercorp.pinpoint.plugin.redis.interceptor;

import java.net.URI;

import redis.clients.jedis.JedisShardInfo;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.plugin.redis.EndPointAccessor;

/**
 * Jedis (redis client) constructor interceptor
 * - trace endPoint
 * 
 * @author jaehong.kim
 *
 */
public class JedisConstructorInterceptor implements AroundInterceptor {

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    public JedisConstructorInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        try {
            if (!validate(target, args)) {
                return;
            }

            final StringBuilder endPoint = new StringBuilder();
            // first arg is host
            if (args[0] instanceof String) {
                endPoint.append(args[0]);
                // second arg is port
                if (args.length >= 2 && args[1] instanceof Integer) {
                    endPoint.append(":").append(args[1]);
                } else {
                    // set default port
                    endPoint.append(":").append(6379);
                }
            } else if (args[0] instanceof URI) {
                final URI uri = (URI) args[0];
                endPoint.append(uri.getHost());
                endPoint.append(":");
                endPoint.append(uri.getPort());
            } else if (args[0] instanceof JedisShardInfo) {
                final JedisShardInfo info = (JedisShardInfo) args[0];
                endPoint.append(info.getHost());
                endPoint.append(":");
                endPoint.append(info.getPort());
            }
            ((EndPointAccessor)target)._$PINPOINT$_setEndPoint(endPoint.toString());
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to BEFORE process. {}", t.getMessage(), t);
            }
        }
    }

    private boolean validate(final Object target, final Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            logger.debug("Invalid arguments. Null or not found args({}).", args);
            return false;
        }

        if (!(target instanceof EndPointAccessor)) {
            logger.debug("Invalid target object. Need field accessor({}).", EndPointAccessor.class.getName());
            return false;
        }

        return true;
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
    }
}