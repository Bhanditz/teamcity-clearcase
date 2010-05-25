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

import com.intellij.execution.ExecutionException;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class CCParseUtil {
  @NonNls private static final String INPUT_DATE_FORMAT = "dd-MMMM-yyyy.HH:mm:ss";
  @NonNls public static final String OUTPUT_DATE_FORMAT = "yyyyMMdd.HHmmss";
  @NonNls public static final String CC_VERSION_SEPARATOR = "@@";
  @NonNls private static final String LOAD = "load ";

  private CCParseUtil() {
  }

  public static List<DirectoryChildElement> readDirectoryVersionContent(ClearCaseConnection connection, final String dirPath)
    throws VcsException {
    List<DirectoryChildElement> subfiles = new ArrayList<DirectoryChildElement>();

    try {
      final InputStream inputStream = connection.listDirectoryContent(dirPath);
      final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          final DirectoryChildElement element = DirectoryChildElement.readFromLSFormat(line, connection);
          if (element != null) {
            subfiles.add(element);
          }
        }
      } finally {
        reader.close();
      }
    } catch (ExecutionException e) {
      throw new VcsException(e);
    } catch (IOException e) {
      throw new VcsException(e);
    }
    return subfiles;
  }

  public static void processChangedFiles(final ClearCaseConnection connection,
                                         final String fromVersion,
                                         final String currentVersion,
                                         final ChangedFilesProcessor fileProcessor) throws ParseException, IOException, VcsException {
    final @Nullable Date lastDate = currentVersion != null ? parseDate(currentVersion) : null;

    final HistoryElementProvider recurseChangesProvider = new HistoryElementProvider(connection.getRecurseChanges(fromVersion));
    final HistoryElementProvider directoryChangesProvider = new HistoryElementProvider(connection.getDirectoryChanges(fromVersion));

    final HistoryElementsMerger changesProvider = new HistoryElementsMerger(recurseChangesProvider, directoryChangesProvider);

    final ChangesInverter inverter = new ChangesInverter(fileProcessor);

    try {
      while (changesProvider.hasNext()) {
        final HistoryElement element = changesProvider.next();
        if (connection.isInsideView(element.getObjectName())) {
          if (lastDate == null || element.getDate().before(lastDate)) {
            if ("checkin".equals(element.getOperation())) {
              if ("create directory version".equals(element.getEvent())) {
                if (element.versionIsInsideView(connection, false)) {
                  inverter.processChangedDirectory(element);
                }
              } else if ("create version".equals(element.getEvent())) {
                if (element.versionIsInsideView(connection, true)) {
                  inverter.processChangedFile(element);
                }
              }
            } else if ("rmver".equals(element.getOperation())) {
              if ("destroy version on branch".equals(element.getEvent())) {
                inverter.processDestroyedFileVersion(element);
              }
            }
          }
        }
      }
    }
    finally {
      changesProvider.close();
    }

    inverter.processCollectedChangesInInvertedOrder();
  }

  private static Date parseDate(final String currentVersion) throws ParseException {
    return getDateFormat().parse(currentVersion);
  }
  
  public static String formatDate(final Date date) {
    return getDateFormat().format(date);    
  }
  

  public static void processChangedDirectory(final HistoryElement element,
                                             final ClearCaseConnection connection,
                                             ChangedStructureProcessor processor) throws IOException, VcsException {

    if (element.getObjectVersionInt() > 0) {
      final String before = element.getObjectName() + CC_VERSION_SEPARATOR + element.getPreviousVersion(connection, true);
      final String after = element.getObjectName() + CC_VERSION_SEPARATOR + element.getObjectVersion();

      final List<DirectoryChildElement> elementsBefore = readDirectoryVersionContent(connection, before);
      final List<DirectoryChildElement> elementsAfter = readDirectoryVersionContent(connection, after);

      Map<String, DirectoryChildElement> filesBefore = collectMap(elementsBefore);
      Map<String, DirectoryChildElement> filesAfter = collectMap(elementsAfter);

      for (String filePath : filesBefore.keySet()) {
        final DirectoryChildElement sourceElement = filesBefore.get(filePath);
        if (!filesAfter.containsKey(filePath)) {
          switch (sourceElement.getType()) {
            case DIRECTORY:
              processor.directoryDeleted(sourceElement);
              break;
            case FILE:
              processor.fileDeleted(sourceElement);
              break;
          }
        }
      }

      for (String filePath : filesAfter.keySet()) {
        final DirectoryChildElement targetElement = filesAfter.get(filePath);
        if (!filesBefore.containsKey(filePath)) {
          switch (targetElement.getType()) {
            case DIRECTORY:
              processor.directoryAdded(targetElement);
              break;
            case FILE:
              processor.fileAdded(targetElement);
              break;
          }
        }
      }

    }
  }

  private static Map<String, DirectoryChildElement> collectMap(final List<DirectoryChildElement> elementsBefore) {
    final HashMap<String, DirectoryChildElement> result = new HashMap<String, DirectoryChildElement>();
    for (DirectoryChildElement element : elementsBefore) {
      result.put(element.getPath(), element);
    }
    return result;
  }

  public static SimpleDateFormat getDateFormat() {
    return new SimpleDateFormat(INPUT_DATE_FORMAT, Locale.US);
  }

  public static String getFileName(final String subdirectory) {
    return new File(subdirectory).getName();
  }

  public static int getVersionInt(final String wholeVersion) {
    final int versSeparator = wholeVersion.lastIndexOf(File.separator);
    return Integer.parseInt(wholeVersion.substring(versSeparator + 1));
  }

  public static String getLastBranch(final String wholeVersion) {
    final int lastSeparatorPos = wholeVersion.lastIndexOf(File.separator);
    final int preLastSeparatorPos = wholeVersion.lastIndexOf(File.separator, lastSeparatorPos - 1);
    return wholeVersion.substring(preLastSeparatorPos + 1, lastSeparatorPos);
  }

  private static class HistoryElementsMerger {
    @NotNull private final HistoryElementProvider myFirstProvider;
    @NotNull private final HistoryElementProvider mySecondProvider;
    @Nullable private HistoryElement myFirstNextElement;
    @Nullable private HistoryElement mySecondNextElement;

    private HistoryElementsMerger(@NotNull final HistoryElementProvider firstProvider,
                                  @NotNull final HistoryElementProvider secondProvider) throws IOException {
      myFirstProvider = firstProvider;
      mySecondProvider = secondProvider;
      readFirstNext();
      readSecondNext();
    }

    @NotNull
    public HistoryElement next() throws IOException, ParseException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (myFirstNextElement == null) {
        return getSecondNext();
      }
      else if (mySecondNextElement == null) {
        return getFirstNext();
      }
      else {
        //noinspection ConstantConditions
        if (myFirstNextElement.getEventID() > mySecondNextElement.getEventID()) {
           return getFirstNext();
        }
        else {
          return getSecondNext();
        }
      }
    }

    public boolean hasNext() {
      return myFirstNextElement != null || mySecondNextElement != null;
    }

    public void close() throws IOException {
      myFirstProvider.close();
      mySecondProvider.close();
    }

    private HistoryElement getFirstNext() throws IOException {
      try {
        //noinspection ConstantConditions
        return myFirstNextElement;
      }
      finally {
        readFirstNext();
      }
    }

    private HistoryElement getSecondNext() throws IOException {
      try {
        //noinspection ConstantConditions
        return mySecondNextElement;
      }
      finally {
        readSecondNext();
      }
    }

    private void readFirstNext() throws IOException {
      myFirstNextElement = readNext(myFirstProvider);
    }

    private void readSecondNext() throws IOException {
      mySecondNextElement = readNext(mySecondProvider);
    }

    @Nullable
    private HistoryElement readNext(@NotNull final HistoryElementProvider provider) throws IOException {
      return provider.hasNext() ? provider.next() : null;
    }
  }

  private static class HistoryElementProvider {
    @NotNull private final BufferedReader myReader;
    @Nullable private HistoryElement myNextElement;
    @Nullable private String myNextLine;

    private HistoryElementProvider(@NotNull final InputStream inputStream) throws IOException {
      myReader = new BufferedReader(new InputStreamReader(inputStream));
      myNextLine = myReader.readLine();
      readNext();
    }

    @NotNull
    public HistoryElement next() throws IOException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        //noinspection ConstantConditions
        return myNextElement;
      }
      finally {
        readNext();
      }
    }

    public boolean hasNext() {
      return myNextElement != null;
    }

    public void close() throws IOException {
      myReader.close();
    }

    private void readNext() throws IOException {
      String line = myNextLine;
      while (line != null) {
        myNextLine = myReader.readLine();
        if (!line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
          while (myNextLine != null && !line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
            line += '\n' + myNextLine;
            myNextLine = myReader.readLine();
          }
        }
        if (line.endsWith(ClearCaseConnection.LINE_END_DELIMITER)) {
          line = line.substring(0, line.length() - ClearCaseConnection.LINE_END_DELIMITER.length());
        }
        myNextElement = HistoryElement.readFrom(line);
        if (myNextElement != null) {
          return;
        }
        line = myNextLine;
      }
      myNextElement = null;
    }
  }
}
