/*
 * Copyright 2017 NAVER Corp.
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

package com.navercorp.pinpoint.web.util;

import com.navercorp.pinpoint.common.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.trace.ServiceTypeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Woonduk Kang(emeroad)
 */
public class ServiceTypeRegistryMockFactory {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<Short, ServiceType> serviceTypeMap = new HashMap<>();

    public void addServiceTypeMock(short typeCode, String typeName) {
        // setup serviceType
        ServiceType mockServiceType = mock(ServiceType.class);
        when(mockServiceType.getCode()).thenReturn(typeCode);
        when(mockServiceType.getName()).thenReturn(typeName);

        if (ServiceTypeCategory.SERVER.contains(typeCode)) {
            logger.debug("mark isWas() {}/{}", mockServiceType.getName(), mockServiceType.getCode());
            when(mockServiceType.isWas()).thenReturn(true);
        }

        this.serviceTypeMap.put(typeCode, mockServiceType);
    }

    public ServiceType getServiceTypeMock(short typeCode) {
        return this.serviceTypeMap.get(typeCode);
    }


    public ServiceTypeRegistryService createMockServiceTypeRegistryService() {

        final ServiceTypeRegistryService serviceTypeRegistryService = mock(ServiceTypeRegistryService.class);
        for (ServiceType serviceType : serviceTypeMap.values()) {
            // setup serviceRegistry
            final String serviceTypeName = serviceType.getName();
            final short serviceTypeCode = serviceType.getCode();
            when(serviceTypeRegistryService.findServiceTypeByName(serviceTypeName)).thenReturn(serviceType);
            when(serviceTypeRegistryService.findServiceType(serviceTypeCode)).thenReturn(serviceType);
            when(serviceTypeRegistryService.findDesc(serviceTypeName)).thenReturn(Collections.singletonList(serviceType));
        }

        return serviceTypeRegistryService;
    }
}
