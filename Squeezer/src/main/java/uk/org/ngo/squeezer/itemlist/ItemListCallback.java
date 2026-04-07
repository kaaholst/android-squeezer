/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.itemlist;

/**
 * Interface to enable automatic removal of callbacks without the need for the
 * programmer to manually unregister the callback.
 * <p>
 * All callbacks must specify the context, usually Activity or Fragment, in which
 * they run, so they can be unregistered via the Android life cycle methods.
 */
public interface ItemListCallback<T> extends ItemReceiver<T> {
    /**
     * @return The context in which the callback runs
     */
    Object getClient();
}

