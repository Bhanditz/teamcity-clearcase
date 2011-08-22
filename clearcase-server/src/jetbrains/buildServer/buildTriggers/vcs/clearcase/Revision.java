package jetbrains.buildServer.buildTriggers.vcs.clearcase;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.util.Dates;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Manuylov
 *         Date: 8/19/11
 */
public abstract class Revision {
  @NonNls @NotNull private static final String FIRST = "FIRST";
  @NonNls public static final char SEPARATOR = '@';

  @Nullable
  public static Revision fromString(@Nullable final String stringRevision) throws ParseException {
    return stringRevision == null ? null : fromNotNullString(stringRevision);
  }

  @NotNull
  public static Revision fromNotNullString(@NotNull final String stringRevision) throws ParseException {
    if (FIRST.equals(stringRevision)) return first();
    final int separatorPos = stringRevision.indexOf(SEPARATOR);
    final Date date = CCParseUtil.parseDate(stringRevision.substring(separatorPos + 1));
    final Long eventId = separatorPos == -1 ? null : CCParseUtil.parseLong(stringRevision.substring(0, separatorPos));
    return new RevisionImpl(eventId, date);
  }

  @NotNull
  public static DateRevision fromChange(@NotNull final HistoryElement change) {
    return new RevisionImpl(change.getEventID(), change.getDate());
  }

  @NotNull
  public static DateRevision current(@NotNull final HistoryElement change) {
    return new RevisionImpl(change.getEventID(), new Date());
  }

  @NotNull
  public static DateRevision fromDate(@NotNull final Date date) {
    return new RevisionImpl(null, date);
  }

  @NotNull
  public static Revision first() {
    return new FirstRevision();
  }

  @Nullable
  public abstract DateRevision getDateRevision();

  public abstract boolean beforeOrEquals(@NotNull final Revision that);

  public abstract void appendLSHistoryOptions(@NotNull final List<String> optionList);

  @NotNull
  public abstract String asString();

  @NotNull
  public abstract Revision shiftToPast(final int minutes);

  @Override
  public String toString() {
    return asString();
  }

  private static class RevisionImpl extends DateRevision {
    @Nullable private final Long myEventId;
    @NotNull private final Date myDate;

    private RevisionImpl(@Nullable final Long eventId, @NotNull final Date date) {
      myEventId = eventId;
      myDate = date;
    }

    @Override
    @Nullable
    public DateRevision getDateRevision() {
      return this;
    }

    @Override
    public boolean beforeOrEquals(@NotNull final Revision _that) {
      if (_that instanceof RevisionImpl) {
        final RevisionImpl that = (RevisionImpl)_that;
        if (myEventId != null && that.myEventId != null) {
          return myEventId <= that.myEventId;
        }
        return myDate.getTime() <= that.myDate.getTime();
      }
      else {
        return !_that.beforeOrEquals(this); // they can't be equal since they have different types
      }
    }

    @Override
    public void appendLSHistoryOptions(@NotNull final List<String> optionList) {
      optionList.add("-since");
      optionList.add(getDateString());
    }

    @NotNull
    @Override
    public Date getDate() {
      return myDate;
    }

    @Override
    @NotNull
    public String asString() {
      return myEventId == null ? getDateString() : myEventId + SEPARATOR + getDateString();
    }

    @NotNull
    @Override
    public Revision shiftToPast(final int minutes) {
      return new RevisionImpl(null, Dates.before(myDate, minutes * Dates.ONE_MINUTE));
    }

    @NotNull
    private String getDateString() {
      return CCParseUtil.formatDate(myDate);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof RevisionImpl)) return false;

      final RevisionImpl revision = (RevisionImpl)o;

      if (!myDate.equals(revision.myDate)) return false;
      return myEventId == null ? revision.myEventId == null : myEventId.equals(revision.myEventId);
    }

    @Override
    public int hashCode() {
      int result = myEventId != null ? myEventId.hashCode() : 0;
      result = 31 * result + myDate.hashCode();
      return result;
    }
  }

  private static class FirstRevision extends Revision {
    @Override
    public DateRevision getDateRevision() {
      return null;
    }

    @Override
    public boolean beforeOrEquals(@NotNull final Revision that) {
      return true;
    }

    @Override
    public void appendLSHistoryOptions(@NotNull final List<String> optionList) {}

    @NotNull
    @Override
    public String asString() {
      return FIRST;
    }

    @NotNull
    @Override
    public Revision shiftToPast(final int minutes) {
      return this;
    }

    @Override
    public boolean equals(final Object o) {
      return this == o || o instanceof FirstRevision;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}