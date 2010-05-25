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

package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

public class HistoryElement {
  
  private final String myUser;
  private final String myDate;
  private final String myObjectName;
  private final String myObjectKind;
  private final String myObjectVersion;
  private final String myOperation;
  private final String myEvent;
  private final String myComment;
  private final String myActivity;
  private final long myEventID;
	
  private static final int EXPECTED_CHANGE_FIELD_COUNT = 9;
  private static final String EVENT = "event ";
  private static final DateFormat ourDateFormat = new SimpleDateFormat(CCParseUtil.OUTPUT_DATE_FORMAT);

  public HistoryElement(
                        final String eventId,
                        final String user,
                        final String date,
                        final String objectName,
                        final String objectKind,
                        final String objectVersion,
                        final String operation,
                        final String event, 
                        final String comment,
                        final String activity
                        ) {
  	myEventID = Long.parseLong(eventId);
    myUser = user;
    myDate = date;
    myObjectName = objectName;
    myObjectKind = objectKind;
    myObjectVersion = objectVersion;
    myOperation = operation;
    myEvent = event;
    myComment = comment;
    myActivity = activity;
  }

	private static HistoryElement createHistoryElement(
                                              final String eventId,	
                                              final String user,
                                              final String date,
                                              final String objectName,
                                              final String objectKind,
                                              final String objectVersion,
                                              final String operation,
                                              final String event,
                                              final String comment,
                                              final String activity) {
    String kind = objectKind, version = objectVersion;
    if ("rmver".equals(operation) && "destroy version on branch".equals(event)) {
      final String extractedVersion = extractVersion(comment);
      if (extractedVersion != null) {
        kind = "version";
        version = extractedVersion;
      }
    }
    return new HistoryElement(eventId, user, date, objectName, kind, version, operation, event, comment, activity);

  }

  @Nullable
  private static String extractVersion(final String comment) {
    int firstPos = comment.indexOf("\""), lastPos = comment.lastIndexOf("\"");
    if (firstPos != -1 && lastPos != -1 && firstPos < lastPos) {
      return comment.substring(firstPos + 1, lastPos);
    }
    return null;
  }

  public static HistoryElement readFrom(final String line) {
    if (!line.startsWith(EVENT)) {
      return null;
    }
    final String[] parts = line.substring(EVENT.length()).split(":", 2);
    if (parts.length < 2) {
      return null;
    }
    final String eventId = parts[0].trim();
    final String[] strings = parts[1].trim().split(ClearCaseConnection.DELIMITER, EXPECTED_CHANGE_FIELD_COUNT);
    if (strings.length < EXPECTED_CHANGE_FIELD_COUNT - 1) {
      return null;
    }
    else if (strings.length == EXPECTED_CHANGE_FIELD_COUNT - 1) {
      return createHistoryElement(eventId,
                                  strings[0],
                                  strings[1],
                                  strings[2],
                                  strings[3],
                                  strings[4],
                                  strings[5],
                                  strings[6],
                                  strings[7],
                                  ""
                                  );
    }
    else {
      return createHistoryElement(eventId,
                                  strings[0],
                                  strings[1],
                                  strings[2],
                                  strings[3],
                                  strings[4],
                                  strings[5],
                                  strings[6],
                                  strings[7],
                                  strings[8]
                                  );
    }
  }

  public String getDateString() {
    return myDate;
  }

  public Date getDate() throws ParseException {
    return ourDateFormat.parse(myDate);
  }

  public String getObjectName() {
    return myObjectName;
  }

  public String getObjectKind() {
    return myObjectKind;
  }

  public String getObjectVersion() {
    return myObjectVersion;
  }

  public String getOperation() {
    return myOperation;
  }

  public String getEvent() {
    return myEvent;
  }

  public String getComment() {
    return myComment;
  }
  

  public String getUser() {
    return myUser;
  }

  public int getObjectVersionInt() {
    return CCParseUtil.getVersionInt(myObjectVersion);
  }

  public String getObjectLastBranch() {
    return CCParseUtil.getLastBranch(myObjectVersion);
  }

  public long getEventID() {
		return myEventID;
	}

  public String getPreviousVersion(final ClearCaseConnection connection, final boolean isDirPath) throws VcsException, IOException {
    return connection.getPreviousVersion(this, isDirPath);
  }

  public boolean versionIsInsideView(final ClearCaseConnection connection, final boolean isFile) throws IOException, VcsException {
    return connection.versionIsInsideView(myObjectName, getObjectVersion(), isFile);
  }

  public String getActivity() {
    return myActivity;
  }

  public String getLogRepresentation() {
    return "\"" + getObjectName() + "\", version \"" + getObjectVersion() + "\", date \"" + getDateString() + "\", operation \"" + getOperation() + "\", event \"" + getEvent() + "\"";
  }

	@Override
	public String toString() {
		return String.format("%s: %s(%s)=>%s", getEventID(), getObjectName(), getOperation(), getEvent());
	}
  
}
