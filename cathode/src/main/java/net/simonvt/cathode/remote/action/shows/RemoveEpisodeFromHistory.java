/*
 * Copyright (C) 2017 Simon Vig Therkildsen
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

package net.simonvt.cathode.remote.action.shows;

import javax.inject.Inject;
import net.simonvt.cathode.api.body.SyncItems;
import net.simonvt.cathode.api.entity.SyncResponse;
import net.simonvt.cathode.api.service.SyncService;
import net.simonvt.cathode.provider.EpisodeDatabaseHelper;
import net.simonvt.cathode.provider.ShowDatabaseHelper;
import net.simonvt.cathode.remote.CallJob;
import net.simonvt.cathode.remote.Flags;
import retrofit2.Call;

public class RemoveEpisodeFromHistory extends CallJob<SyncResponse> {

  @Inject transient SyncService syncService;

  @Inject transient ShowDatabaseHelper showHelper;
  @Inject transient EpisodeDatabaseHelper episodeHelper;

  private long traktId;

  private int season;

  private int episode;

  public RemoveEpisodeFromHistory(long traktId, int season, int episode) {
    super(Flags.REQUIRES_AUTH);
    this.traktId = traktId;
    this.season = season;
    this.episode = episode;
  }

  @Override public String key() {
    return "RemoveEpisodeFromHistory"
        + "&traktId="
        + traktId
        + "&season="
        + season
        + "&episode="
        + episode;
  }

  @Override public int getPriority() {
    return PRIORITY_ACTIONS;
  }

  @Override public boolean allowDuplicates() {
    return true;
  }

  @Override public Call<SyncResponse> getCall() {
    SyncItems items = new SyncItems();
    items.show(traktId).season(season).episode(episode);
    return syncService.unwatched(items);
  }

  @Override public void handleResponse(SyncResponse response) {
    final long showId = showHelper.getId(traktId);
    final long episodeId = episodeHelper.getId(showId, season, episode);
    episodeHelper.removeFromHistory(episodeId);
  }
}
