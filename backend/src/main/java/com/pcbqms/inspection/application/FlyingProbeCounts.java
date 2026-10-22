package com.pcbqms.inspection.application;

record FlyingProbeCounts(int tested, int good, int bad) {
    static final FlyingProbeCounts ZERO = new FlyingProbeCounts(0, 0, 0);

    FlyingProbeCounts deltaFrom(FlyingProbeCounts previous) {
        if (tested < 0 || good < 0 || bad < 0 || (long) good + bad != tested) {
            throw new IllegalArgumentException("飞针累计计数不一致");
        }
        FlyingProbeCounts delta = new FlyingProbeCounts(
            tested - previous.tested, good - previous.good, bad - previous.bad);
        if (delta.tested < 0 || delta.good < 0 || delta.bad < 0 || (long) delta.good + delta.bad != delta.tested) {
            throw new IllegalArgumentException("飞针累计计数回退");
        }
        return delta;
    }
}
