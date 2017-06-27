/*
 * Copyright (C) 2015 Simon Vig Therkildsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.simonvt.cathode.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import javax.inject.Inject;
import net.simonvt.cathode.Injector;
import net.simonvt.cathode.jobqueue.AuthJobService;
import net.simonvt.cathode.jobqueue.JobManager;
import net.simonvt.cathode.remote.sync.SyncWatching;

public class SyncWatchingReceiver extends BroadcastReceiver {

  @Inject JobManager jobManager;

  @Override public void onReceive(Context context, Intent intent) {
    Injector.obtain().inject(this);
    jobManager.addJob(new SyncWatching());
    Intent i = new Intent(context, AuthJobService.class);
    context.startService(i);
  }
}
