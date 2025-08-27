package com.xenonbyte.anr.dump

import android.app.Application
import com.xenonbyte.activitywatcher.ActivityWatcher

/**
 * Activity数据转储器
 *
 * @author xubo
 */
class ActivityDumper : Dumper<ActivityData>("ActivityDumper") {

    override fun collectData(app: Application): ActivityData {
        return ActivityData(ActivityWatcher.getStackJson(), !ActivityWatcher.isAppBackground())
    }

}