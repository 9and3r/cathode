/*
 * Copyright (C) 2013 Simon Vig Therkildsen
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
package net.simonvt.cathode;

import android.accounts.Account;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import javax.inject.Inject;
import net.simonvt.cathode.api.util.TimeUtils;
import net.simonvt.cathode.event.AuthFailedEvent;
import net.simonvt.cathode.event.AuthFailedEvent.OnAuthFailedListener;
import net.simonvt.cathode.jobqueue.AuthJobHandler;
import net.simonvt.cathode.jobqueue.DataJobHandler;
import net.simonvt.cathode.jobqueue.JobHandler;
import net.simonvt.cathode.jobqueue.JobManager;
import net.simonvt.cathode.jobscheduler.AuthJobHandlerJob;
import net.simonvt.cathode.jobscheduler.DataJobHandlerJob;
import net.simonvt.cathode.jobscheduler.Jobs;
import net.simonvt.cathode.remote.ForceUpdateJob;
import net.simonvt.cathode.remote.UpdateShowCounts;
import net.simonvt.cathode.remote.sync.SyncJob;
import net.simonvt.cathode.remote.sync.SyncUserActivity;
import net.simonvt.cathode.remote.sync.SyncWatching;
import net.simonvt.cathode.remote.sync.movies.SyncAnticipatedMovies;
import net.simonvt.cathode.remote.sync.movies.SyncUpdatedMovies;
import net.simonvt.cathode.remote.sync.shows.SyncAnticipatedShows;
import net.simonvt.cathode.remote.sync.shows.SyncUpdatedShows;
import net.simonvt.cathode.remote.upgrade.EnsureSync;
import net.simonvt.cathode.remote.upgrade.UpperCaseGenres;
import net.simonvt.cathode.settings.Accounts;
import net.simonvt.cathode.settings.FirstAiredOffsetPreference;
import net.simonvt.cathode.settings.Settings;
import net.simonvt.cathode.settings.TraktTimestamps;
import net.simonvt.cathode.settings.UpcomingTimePreference;
import net.simonvt.cathode.settings.login.LoginActivity;
import net.simonvt.cathode.ui.HomeActivity;
import net.simonvt.cathode.ui.shows.upcoming.UpcomingSortByPreference;
import net.simonvt.cathode.util.DateUtils;
import net.simonvt.cathode.util.MainHandler;
import timber.log.Timber;

public class CathodeApp extends Application {

  private static final int AUTH_NOTIFICATION = 2;

  private static final long SYNC_DELAY = 15 * DateUtils.MINUTE_IN_MILLIS;

  private static CathodeApp instance;

  private SharedPreferences settings;

  @Inject JobManager jobManager;

  private int homeActivityResumedCount;
  private long lastSync;

  @Override public void onCreate() {
    super.onCreate();
    instance = this;

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());

      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().penaltyLog().build());
    } else {
      Fabric.with(this, new Crashlytics());
      Timber.plant(new CrashlyticsTree());
    }

    UpcomingSortByPreference.init(this);
    UpcomingTimePreference.init(this);
    FirstAiredOffsetPreference.init(this);

    settings = PreferenceManager.getDefaultSharedPreferences(this);

    upgrade();

    Injector.install(this);
    Injector.obtain().inject(this);

    AuthFailedEvent.registerListener(authFailedListener);

    final boolean isLoggedIn = settings.getBoolean(Settings.TRAKT_LOGGED_IN, false);
    if (isLoggedIn) {
      Accounts.setupAccount(this);
    } else if (!isLoggedIn) {
      Accounts.removeAccount(this);
    }

    registerActivityLifecycleCallbacks(new SimpleActivityLifecycleCallbacks() {

      @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof HomeActivity) {
          homeResumed();
        }
      }

      @Override public void onActivityPaused(Activity activity) {
        if (activity instanceof HomeActivity) {
          homePaused();
        }
      }
    });

    if (Jobs.usesScheduler()) {
      SyncUpdatedShows.schedulePeriodic(this);
      SyncUpdatedMovies.schedulePeriodic(this);
      SyncUserActivity.schedulePeriodic(this);
      AuthJobHandlerJob.schedulePeriodic(this);
      DataJobHandlerJob.schedulePeriodic(this);
    }
  }

  private Runnable syncRunnable = new Runnable() {
    @Override public void run() {
      Timber.d("Performing periodic sync");
      final long currentTime = System.currentTimeMillis();
      final long lastFullSync = settings.getLong(Settings.LAST_FULL_SYNC, 0);

      if (lastFullSync + DateUtils.DAY_IN_MILLIS < currentTime) {
        jobManager.addJob(new SyncJob());
      } else {
        jobManager.addJob(new SyncUserActivity());
        jobManager.addJob(new SyncWatching());
      }

      lastSync = System.currentTimeMillis();
      MainHandler.postDelayed(this, SYNC_DELAY);
    }
  };

  private void homeResumed() {
    homeActivityResumedCount++;

    if (homeActivityResumedCount == 1) {
      Timber.d("Starting periodic sync");
      final long currentTime = System.currentTimeMillis();
      if (lastSync + SYNC_DELAY < currentTime) {
        syncRunnable.run();
      } else {
        final long delay = Math.max(SYNC_DELAY - (currentTime - lastSync), 0);
        MainHandler.postDelayed(syncRunnable, delay);
      }

      AuthJobHandler.getInstance().registerListener(authJobListener);
      DataJobHandler.getInstance().registerListener(dataJobListener);
    }
  }

  private void homePaused() {
    homeActivityResumedCount--;
    if (homeActivityResumedCount == 0) {
      Timber.d("Pausing periodic sync");
      MainHandler.removeCallbacks(syncRunnable);
      AuthJobHandler.getInstance().unregisterListener(authJobListener);
      DataJobHandler.getInstance().unregisterListener(dataJobListener);
    }
  }

  private JobHandler.JobHandlerListener authJobListener = new JobHandler.JobHandlerListener() {

    @Override public void onQueueEmpty() {
      Timber.d("Auth job queue empty");
    }

    @Override public void onQueueFailed() {
      Timber.d("Auth job queue failed");
    }
  };

  private JobHandler.JobHandlerListener dataJobListener = new JobHandler.JobHandlerListener() {

    @Override public void onQueueEmpty() {
      Timber.d("Data job queue empty");
    }

    @Override public void onQueueFailed() {
      Timber.d("Data job queue failed");
    }
  };

  private void upgrade() {
    final int currentVersion = settings.getInt(Settings.VERSION_CODE, -1);

    if (currentVersion == -1) {
      settings.edit().putInt(Settings.VERSION_CODE, BuildConfig.VERSION_CODE).apply();
      return;
    }

    if (currentVersion != BuildConfig.VERSION_CODE) {
      if (currentVersion < 20002) {
        Accounts.removeAccount(this);
        settings.edit().clear().apply();
      }
      if (currentVersion < 20501) {
        TraktTimestamps.clear(this);
      }
      if (currentVersion < 21001) {
        MainHandler.post(new Runnable() {
          @Override public void run() {
            jobManager.addJob(new ForceUpdateJob());
          }
        });
      }
      if (currentVersion <= 21001) {
        MainHandler.post(new Runnable() {
          @Override public void run() {
            jobManager.addJob(new UpdateShowCounts());
          }
        });
      }
      if (currentVersion <= 31001) {
        Account account = Accounts.getAccount(this);

        if (account != null) {
          ContentResolver.setIsSyncable(account, BuildConfig.AUTHORITY_DUMMY_CALENDAR, 1);
          ContentResolver.setSyncAutomatically(account, BuildConfig.AUTHORITY_DUMMY_CALENDAR, true);
          ContentResolver.addPeriodicSync(account, BuildConfig.AUTHORITY_DUMMY_CALENDAR,
              new Bundle(), 12 * DateUtils.HOUR_IN_SECONDS);
        }

        Accounts.requestCalendarSync(this);
      }
      if (currentVersion <= 31003) {
        settings.edit().remove("showHidden").apply();
      }
      if (currentVersion <= 37000) {
        settings.edit().remove(Settings.START_PAGE).apply();
      }
      if (currentVersion <= 39003) {
        MainHandler.post(new Runnable() {
          @Override public void run() {
            jobManager.addJob(new SyncAnticipatedShows());
            jobManager.addJob(new SyncAnticipatedMovies());
          }
        });
      }
      if (currentVersion <= 40102) {
        MainHandler.post(new Runnable() {
          @Override public void run() {
            jobManager.addJob(new EnsureSync());
          }
        });
      }
      if (currentVersion <= 40104) {
        MainHandler.post(new Runnable() {
          @Override public void run() {
            jobManager.addJob(new UpperCaseGenres());
          }
        });
      }
      if (currentVersion <= 50303) {
        if (Jobs.usesScheduler()) {
          final boolean isLoggedIn = settings.getBoolean(Settings.TRAKT_LOGGED_IN, false);
          if (isLoggedIn) {
            Account account = Accounts.getAccount(this);
            ContentResolver.removePeriodicSync(account, BuildConfig.PROVIDER_AUTHORITY,
                new Bundle());
            ContentResolver.setSyncAutomatically(account, BuildConfig.PROVIDER_AUTHORITY, false);
            ContentResolver.setIsSyncable(account, BuildConfig.PROVIDER_AUTHORITY, 0);
          }
        }

        final String showsLastUpdated = settings.getString(Settings.SHOWS_LAST_UPDATED, null);
        if (showsLastUpdated != null) {
          final long showsLastUpdatedMillis = TimeUtils.getMillis(showsLastUpdated);
          settings.edit().putLong(Settings.SHOWS_LAST_UPDATED, showsLastUpdatedMillis).apply();
        }
        final String moviesLastUpdated = settings.getString(Settings.MOVIES_LAST_UPDATED, null);
        if (moviesLastUpdated != null) {
          final long moviesLastUpdatedMillis = TimeUtils.getMillis(moviesLastUpdated);
          settings.edit().putLong(Settings.MOVIES_LAST_UPDATED, moviesLastUpdatedMillis).apply();
        }
      }

      MainHandler.post(new Runnable() {
        @Override public void run() {
          jobManager.addJob(new SyncJob());
        }
      });

      settings.edit().putInt(Settings.VERSION_CODE, BuildConfig.VERSION_CODE).apply();
    }
  }

  private OnAuthFailedListener authFailedListener = new OnAuthFailedListener() {
    @Override public void onAuthFailed() {
      Timber.i("onAuthFailure");

      SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CathodeApp.this);
      settings.edit().putBoolean(Settings.TRAKT_LOGGED_IN, false).apply();

      Intent intent = new Intent(CathodeApp.this, LoginActivity.class);
      intent.setAction(HomeActivity.ACTION_LOGIN);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      PendingIntent pi = PendingIntent.getActivity(CathodeApp.this, 0, intent, 0);

      Notification.Builder builder = new Notification.Builder(CathodeApp.this) //
          .setSmallIcon(R.drawable.ic_noti_error)
          .setTicker(getString(R.string.auth_failed))
          .setContentTitle(getString(R.string.auth_failed))
          .setContentText(getString(R.string.auth_failed_desc))
          .setContentIntent(pi)
          .setPriority(Notification.PRIORITY_HIGH)
          .setAutoCancel(true);

      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      nm.notify(AUTH_NOTIFICATION, builder.build());
    }
  };
}
