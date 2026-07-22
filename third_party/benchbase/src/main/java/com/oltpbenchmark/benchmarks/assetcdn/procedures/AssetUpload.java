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

package com.oltpbenchmark.benchmarks.assetcdn.procedures;

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.assetcdn.AssetCdnConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Payment-analog: the read-then-cross-table-write transaction. A new asset revision is published:
 * read the asset's current version, insert the new ASSET_VERSION row, then bump ASSET's
 * current_version/updated_tick - two different tables touched atomically, the same shape as
 * Payment's warehouse+customer update.
 */
public class AssetUpload extends Procedure {

  public final SQLStmt GetCurrentVersion =
      new SQLStmt(
          "SELECT current_version FROM " + AssetCdnConstants.TABLENAME_ASSET + " WHERE asset_id = ?");

  public final SQLStmt InsertVersion =
      new SQLStmt(
          "INSERT INTO "
              + AssetCdnConstants.TABLENAME_ASSET_VERSION
              + " (asset_id, version, storage_uri, size_bytes, created_tick)"
              + " VALUES (?, ?, ?, ?, ?)");

  public final SQLStmt UpdateAsset =
      new SQLStmt(
          "UPDATE "
              + AssetCdnConstants.TABLENAME_ASSET
              + " SET current_version = ?, updated_tick = ? WHERE asset_id = ?");

  public void run(Connection conn, long assetId, long sizeBytes, long tick) throws SQLException {
    int currentVersion;
    try (PreparedStatement stmt = this.getPreparedStatement(conn, GetCurrentVersion, assetId)) {
      try (ResultSet r = stmt.executeQuery()) {
        if (!r.next()) {
          throw new UserAbortException("Unknown asset " + assetId);
        }
        currentVersion = r.getInt(1);
      }
    }

    int newVersion = currentVersion + 1;
    String storageUri = "s3://assets/" + assetId + "/v" + newVersion;

    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, InsertVersion, assetId, newVersion, storageUri, sizeBytes, tick)) {
      stmt.executeUpdate();
    }
    try (PreparedStatement stmt =
        this.getPreparedStatement(conn, UpdateAsset, newVersion, tick, assetId)) {
      stmt.executeUpdate();
    }
  }
}
