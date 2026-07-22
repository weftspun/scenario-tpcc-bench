/*
 * Copyright 2020 by OLTPBenchmark Project
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
 *
 */

package com.oltpbenchmark.benchmarks.cassie;

public abstract class CassieConstants {
  public static final String TABLENAME_CANVAS = "CANVAS";
  public static final String TABLENAME_STROKE = "STROKE";
  public static final String TABLENAME_STROKE_POINT = "STROKE_POINT";
  public static final String TABLENAME_BEAUTIFIED_STROKE = "BEAUTIFIED_STROKE";
  public static final String TABLENAME_CANVAS_SUBSCRIBER = "CANVAS_SUBSCRIBER";

  // Scale factor is "how many canvases", matching zonefabric's
  // "scale factor = zone count" convention.
  public static final int STROKES_PER_CANVAS_SEED = 5;
  public static final int POINTS_PER_SEED_STROKE = 20;
  public static final int SUBSCRIBERS_PER_CANVAS = 10;

  // How many points AppendStrokePoints writes per invocation - one
  // transaction models one completed freehand stroke's worth of input,
  // not a single point at a time (real input is batched client-side
  // before it ever reaches durable storage).
  public static final int POINTS_PER_APPENDED_STROKE = 30;

  public static final double CANVAS_EXTENT = 4096.0;
}
