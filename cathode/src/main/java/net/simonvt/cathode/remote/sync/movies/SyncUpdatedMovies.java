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
package net.simonvt.cathode.remote.sync.movies;

import java.util.List;
import javax.inject.Inject;
import net.simonvt.cathode.api.entity.Movie;
import net.simonvt.cathode.api.entity.UpdatedItem;
import net.simonvt.cathode.api.service.MoviesService;
import net.simonvt.cathode.jobqueue.Job;
import net.simonvt.cathode.provider.MovieWrapper;

public class SyncUpdatedMovies extends Job {

  private static final int LIMIT = 100;

  @Inject transient MoviesService moviesService;

  private String updatedSince;

  private int page;

  public SyncUpdatedMovies(String updatedSince, int page) {
    super();
    this.updatedSince = updatedSince;
    this.page = page;
  }

  @Override public String key() {
    return "SyncUpdatedMovies" + "&updatedSince=" + updatedSince + "&page=" + page;
  }

  @Override public int getPriority() {
    return PRIORITY_2;
  }

  @Override public void perform() {
    List<UpdatedItem> updated = moviesService.updated(updatedSince, page, LIMIT);

    for (UpdatedItem item : updated) {
      final String updatedAt = item.getUpdatedAt();
      final Movie movie = item.getMovie();
      final long traktId = movie.getIds().getTrakt();

      final boolean exists = MovieWrapper.exists(getContentResolver(), traktId);
      if (exists) {
        final boolean needsUpdate =
            MovieWrapper.needsUpdate(getContentResolver(), traktId, updatedAt);
        if (needsUpdate) {
          queue(new SyncMovie(traktId));
        }
      }
    }

    if (updated.size() >= LIMIT) {
      queue(new SyncUpdatedMovies(updatedSince, page + 1));
    }
  }
}
