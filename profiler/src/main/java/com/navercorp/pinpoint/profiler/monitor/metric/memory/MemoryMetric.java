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

package com.navercorp.pinpoint.profiler.monitor.metric.memory;

import com.codahale.metrics.Gauge;
import com.navercorp.pinpoint.profiler.monitor.codahale.MetricMonitorValues;

/**
 * @author dawidmalina
 * @author HyunGil Jeong
 */
public interface MemoryMetric {

    Gauge<Long> EMPTY_LONG_GAUGE = new MetricMonitorValues.EmptyGauge<Long>(-1L);
    Gauge<Double> EMPTY_DOUBLE_GAUGE = new MetricMonitorValues.EmptyGauge<Double>(-1D);

    Long heapMax();

    Long heapUsed();

    Long nonHeapMax();

    Long nonHeapUsed();

    Double newGenUsage();

    Double oldGenUsage();

    Double codeCacheUsage();

    Double survivorUsage();

    Double permGenUsage();

    Double metaspaceUsage();
}
