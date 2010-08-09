/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.buildServer.vcs.clearcase;

public class CCStorage {

  private String myTag;
  private String myGlobalPath;
  private String myServerHost;

  CCStorage(final String serverHost, final String tag, final String globalPath){
      myTag = tag;
      myGlobalPath = globalPath;
      myServerHost = serverHost;
  }
  
  public String getServerHost() {
    return myServerHost;
  }

  public String getTag() {
    return myTag;
  }

  public String getGlobalPath() {
    return myGlobalPath;
  }
  
}