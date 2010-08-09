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

import java.io.File;

import jetbrains.buildServer.vcs.clearcase.CTool.VobObjectParser;



public class CCVob {
  
  private String myTag;
  private String myGlobalPath;
  private boolean isAvailable;
  private String myRegion;
  private String myServerHost;

  public CCVob(final String tag){
    myTag = tag;
  }
  
  CCVob(final String tag, final String region, final String host, final String globalPath){
    myTag = tag;
    myRegion = region;
    myServerHost = host;
    myGlobalPath = globalPath;
  }
  
  public String getTag() {
    return myTag;
  }
  
  public String getRegion() {
    return myRegion;
  }
  
  public String getServerHost() {
    return myServerHost;
  }

  public String getGlobalPath() {
    return myGlobalPath;
  }
  
  @Override
  public String toString () {
    return String.format("{CCVob: region=\"%s\", host=\"%s\", tag=\"%s\", global=\"%s\"}", getRegion(), getServerHost(), getTag(), getGlobalPath()); 
  }
  
  public void create(final String reason) throws CCException {
    try {
      final VobObjectParser result = CTool.createVob(myTag, reason);
      myGlobalPath =  result.getGlobalPath();
      isAvailable = true;
    } catch (Exception e) {
      throw new CCException(e);
    }
    
  }
  
  public void load(final File dump, String reason) throws CCException {
    try {
      final VobObjectParser result = CTool.importVob(myTag, dump, reason);
      myGlobalPath = result.getGlobalPath();

    } catch (Exception e) {
      throw new CCException(e);

    }
  }
  
  public void drop() throws CCException {
    try {
      if(isAvailable()){
        CTool.dropVob(getGlobalPath());
        isAvailable  = false;
      }
    } catch (Exception e) {
      throw new CCException(e);
    }
  }

  public boolean isAvailable() {
    return isAvailable;
  }

}