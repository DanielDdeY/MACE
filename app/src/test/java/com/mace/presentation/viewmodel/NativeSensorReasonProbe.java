package com.mace.presentation.viewmodel;

import com.mace.domain.model.MetricState;
import com.mace.infrastructure.nativebridge.*;
import java.util.concurrent.TimeUnit;

/** Manual/CI real native integration, deliberately WITHOUT elevating or opening Preview. */
public final class NativeSensorReasonProbe {
    public static void main(String[] args) throws Exception {
        try (var functions=NativeFunctions.load()) {
            var sensors=new FfmSensorsAdapter(functions);
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            do {
                var sample=sensors.fetchHardwareSnapshot();
                if (sample.cpu().temperatureCelsius().state()==MetricState.NOT_ELEVATED) {
                    if (!sample.cpu().usagePercent().isValid() || !sample.memory().totalBytes().isValid())
                        throw new IllegalStateException("Independent Windows metrics must work");
                    System.out.println("REAL: ABI="+functions.abiInfo().abiVersion()+" CPU state=9; Windows CPU/RAM VALID");
                    System.out.println(MetricMessages.message(sample.cpu().temperatureCelsius().state()));
                    return;
                }
                Thread.sleep(50);
            } while (System.nanoTime()<until);
            throw new IllegalStateException("Expected NOT_ELEVATED with published helper in an unelevated process");
        }
    }
}
