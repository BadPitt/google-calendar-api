package com.example.badpitt.googlecalendarapi;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.*;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CalendarRequestManager
{
    private static final String CALENDAR_ID = "qv2g428rlv8nir700cp32ers6o@group.calendar.google.com"; //"primary" - main user's calendar
    private static final int MAX_EVENTS_COUNT = 10;

    public static Observable<String> getNewEventObservable(final GoogleAccountCredential credential)
    {
        return Observable.defer(() -> {
            try
            {
                return Observable.just(addNewEventToCalendar(credential));
            }
            catch (IOException ioe)
            {
                //TODO: verify it
                return Observable.error(ioe);
            }
        })
                         .observeOn(AndroidSchedulers.mainThread())
                         .subscribeOn(Schedulers.io());
    }

    public static Observable<List<String>> getEventsListObservable(final GoogleAccountCredential credential)
    {
        return Observable.defer(() -> {
            try
            {
                return Observable.just(getEventsFromCalendar(credential));
            }
            catch (IOException ioe)
            {
                //TODO: verify it
                return Observable.error(ioe);
            }
        })
                         .observeOn(AndroidSchedulers.mainThread())
                         .subscribeOn(Schedulers.io());
    }

    private static String addNewEventToCalendar(GoogleAccountCredential credential) throws IOException
    {
        DateTime startDateTime = new DateTime("2016-10-28T09:00:00-07:00");
        DateTime endDateTime = new DateTime("2016-10-28T10:00:00-07:00");
        String currentTimeZone = "America/Los_Angeles";

        Event event = new Event()
                .setSummary("New task in the calendar")
                .setDescription("I want to cut off my hair");

        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone(currentTimeZone);
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(endDateTime)
                .setTimeZone(currentTimeZone);
        event.setEnd(end);

        String[] recurrence = new String[]{"RRULE:FREQ=DAILY;COUNT=2"};
        event.setRecurrence(Arrays.asList(recurrence));

        EventAttendee[] attendees = new EventAttendee[]{
                new EventAttendee().setEmail("lpage@example.com"),
                new EventAttendee().setEmail("sbrin@example.com"),
                };
        event.setAttendees(Arrays.asList(attendees));

        EventReminder[] reminderOverrides = new EventReminder[]{
                new EventReminder().setMethod("email").setMinutes(24 * 60),
                new EventReminder().setMethod("popup").setMinutes(10),
                };
        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(Arrays.asList(reminderOverrides));
        event.setReminders(reminders);

        event = CalendarService.getInstance(credential)
                               .events()
                               .insert(CALENDAR_ID, event)
                               .execute();
        return event.getHtmlLink();
    }

    private static List<String> getEventsFromCalendar(GoogleAccountCredential credential) throws IOException
    {
        DateTime now = new DateTime(System.currentTimeMillis());
        List<String> eventStrings = new ArrayList<>();
        Events events = CalendarService.getInstance(credential)
                                       .events()
                                       .list(CALENDAR_ID)
                                       .setMaxResults(MAX_EVENTS_COUNT)
                                       .setTimeMin(now)
                                       .setOrderBy("startTime")
                                       .setSingleEvents(true)
                                       .execute();
        List<Event> items = events.getItems();

        for (Event event : items)
        {
            DateTime start = event.getStart().getDateTime();
            if (start == null)
            {
                // All-day events don't have start times, so just use
                // the start date.
                start = event.getStart().getDate();
            }
            eventStrings.add(
                    String.format("%s (%s)", event.getSummary(), start));
        }
        return eventStrings;
    }
}
