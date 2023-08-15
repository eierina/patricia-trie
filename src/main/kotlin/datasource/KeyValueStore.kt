/*
 * Copyright 2023 Edoardo Ierina
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package datasource

/**
 * Definition of KeyValueStore interface.
 */
interface KeyValueStore {

    /**
     * Retrieves the value associated with a given key.
     *
     * @param key The key for which the value is to be retrieved.
     * @return The value associated with the key, or null if the key is not present in the store.
     */
    fun get(key: ByteArray): ByteArray?

    /**
     * Inserts or updates a key-value pairing in the store.
     *
     * @param key The key to be associated with the given value.
     * @param value The value to be stored.
     */
    fun put(key: ByteArray, value: ByteArray)

    /**
     * Checks whether the store contains any key-value pairings.
     *
     * @return True if the store is empty, false otherwise.
     */
    fun isEmpty(): Boolean
}