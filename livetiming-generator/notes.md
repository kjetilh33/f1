2. Add a fallback threshold (safeguard against non-race sessions)

In DbDataFeed.java#L119, queryList randomly picks from austrianGPPractice1, austrianGPQalifying, austrianGP, and britishGP.

If a dataset never contains "SessionStatus":"Started" (e.g. if the recording started after that event or if practice/qualifying uses different markers), inPreRace will stay true forever and replay the entire race at 10 ms per event.

Fix: Add a time-based safety fallback (e.g. auto-transition after 70 minutes of recorded stream data):

java
Instant firstRecord = null; // restore usage of firstRecord as a fallback timer
// Inside rs.next():
if (firstRecord == null) {
firstRecord = liveTimingMessage.timestamp();
}
boolean isSessionStartEvent = inPreRace && "SessionData".equals(category)
&& message.contains("\"SessionStatus\"") && message.contains("\"Started\"");
boolean isFallbackTimeout = inPreRace
&& Duration.between(firstRecord, liveTimingMessage.timestamp()).toMinutes() >= 70;
if (isSessionStartEvent || isFallbackTimeout) {
LOG.info("Transitioning to real-time playback (Reason: {})...",
isSessionStartEvent ? "SessionStatus: Started" : "Fallback timeout reached");
inPreRace = false;
queryStart = Instant.now();
raceRecordStart = liveTimingMessage.timestamp();
}
3. Fix the Math.min(2000, ...) timing drift

In the race playback block:

java
Duration queryDuration = Duration.between(queryStart, Instant.now());
Duration recordDuration = Duration.between(raceRecordStart, liveTimingMessage.timestamp());
if (queryDuration.compareTo(recordDuration) < 0) {
long sleepMillies = Math.min(2000, recordDuration.toMillis() - queryDuration.toMillis());
Thread.sleep(sleepMillies);
}

If there is a lull in messages (e.g. a 10-second gap), this code sleeps only 2 seconds and then emits the message 8 seconds ahead of schedule.

To sleep in small intervals (staying responsive to run.get()) while accurately waiting for the full gap:

java
Duration recordDuration = Duration.between(raceRecordStart, liveTimingMessage.timestamp());
while (run.get()) {
long remainingSleep = recordDuration.toMillis() - Duration.between(queryStart, Instant.now()).toMillis();
if (remainingSleep <= 0) {
break;
}
Thread.sleep(Math.min(500, remainingSleep));
}
4. Clean up unused variable and catch InterruptedException
   In DbDataFeed.java#L134, Instant firstRecord = null; is currently unused unless used for the fallback mentioned in item 2.
   When close() is called, Thread.sleep throws InterruptedException. Currently line 178 catches Exception and wraps it in a RuntimeException, logging an unexpected stack trace. Catching InterruptedException explicitly and breaking from the loop makes shutdown clean:
   java
   } catch (InterruptedException e) {
   LOG.info("Replay loop interrupted, shutting down.");
   Thread.currentThread().interrupt();
   break;
   } catch (Exception e) {
   throw new RuntimeException(e);
   }
5. Make pre-race sleep configurable

Currently Thread.sleep(10) is hardcoded. If you have 4,000 pre-race messages, 10 ms takes ~40 seconds.

Exposing this via MicroProfile Config allows adjusting or skipping it when needed:

java
private final int preRaceSleepMs =
ConfigProvider.getConfig().getOptionalValue("replay.prerace.sleep.ms", Integer.class).orElse(5);