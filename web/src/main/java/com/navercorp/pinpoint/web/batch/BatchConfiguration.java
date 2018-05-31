package com.navercorp.pinpoint.web.batch;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.ImportResource;
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
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author minwoo.jung<minwoo.jung@navercorp.com>
 *
 */
@Configuration
@Conditional(BatchConfiguration.Condition.class)
@ImportResource("classpath:/batch/applicationContext-batch-schedule.xml")
public class BatchConfiguration{
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);
    
    static class Condition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.PARSE_CONFIGURATION;
        }
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Properties properties=new Properties();
            Resource resource = context.getResourceLoader().getResource("classpath:/batch.properties");
            try {
                properties.load(resource.getInputStream());
                final String enable = properties.getProperty("batch.enable");
                if(enable == null) {
                    return false;
                }

                return Boolean.valueOf(enable.trim());
            } catch (Exception e) {
                logger.error("Exception occurred while batch configuration" , e);
            }
            
            return false;
            
        }
   }
}
