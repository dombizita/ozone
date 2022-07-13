/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.recon.tasks;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hdds.annotation.InterfaceStability;
import org.apache.hadoop.ozone.om.OMMetadataManager;
import org.apache.hadoop.ozone.recon.recovery.ReconOMMetadataManager;

/**
 * Interface used to denote a Recon task that needs to act on OM DB events.
 */
@InterfaceStability.Evolving
public interface ReconOmTask {

  /**
   * Return task name.
   * @return task name
   */
  String getTaskName();

  /**
   * Process a set of OM events on tables that the task is listening on.
   * @param events Set of events to be processed by the task.
   * @return ReconTaskResult with information about the task.
   */
  ReconTaskResult process(OMUpdateEventBatch events);

  /**
   * Reprocess on tables that the task is listening on.
   * @param omMetadataManager Recon OM Metadata manager instance.
   * @return ReconTaskResult with information about the task.
   */
  ReconTaskResult reprocess(ReconOMMetadataManager omMetadataManager);

  class ReconTaskResult {
    private String taskName;
    private boolean success;
    private Long lastProcessedSequenceNumber;

    public ReconTaskResult(String taskName, boolean success, Long lastProcessedSequenceNumber) {
      this.taskName = taskName;
      this.success = success;
      this.lastProcessedSequenceNumber = lastProcessedSequenceNumber;
    }

    public String getTaskName() {
      return taskName;
    }

    public void setTaskName(String taskName) {
      this.taskName = taskName;
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public Long getLastProcessedSequenceNumber() {
      return lastProcessedSequenceNumber;
    }

    public void setLastProcessedSequenceNumber(Long lastProcessedSequenceNumber) {
      this.lastProcessedSequenceNumber = lastProcessedSequenceNumber;
    }
  }
}
