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

package net.simonvt.cathode.api.entity;

import net.simonvt.cathode.api.enumeration.Action;
import net.simonvt.cathode.api.enumeration.ItemType;

public class HistoryItem {

  long id;

  IsoTime watchedAt;

  Action action;

  ItemType type;

  Movie movie;

  Show show;

  Season season;

  Episode episode;

  public long getId() {
    return id;
  }

  public IsoTime getWatchedAt() {
    return watchedAt;
  }

  public Action getAction() {
    return action;
  }

  public ItemType getType() {
    return type;
  }

  public Movie getMovie() {
    return movie;
  }

  public Show getShow() {
    return show;
  }

  public Season getSeason() {
    return season;
  }

  public Episode getEpisode() {
    return episode;
  }
}
