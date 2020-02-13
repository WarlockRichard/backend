/*
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
 */

CREATE TABLE user_meta
(
  user_id BIGINT NOT NULL,
  gdrive_folder_id VARCHAR DEFAULT NULL,
  CONSTRAINT user_meta_account_id_fk FOREIGN KEY (user_id) REFERENCES account (id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX user_meta_user_id_uindex ON user_meta (user_id);