# Project Roadmap

## 🚀 High Priority
- [ ] <!-- TODO: --> Timing stats data with array/object notation handling
- [ ] <!-- TODO: --> Identify laps and laps stats
- [x] <!-- TODO: --> SignalR refactoring

## 🛠️ Features
- [ ] ...

### TimingAppData
Stints:
- Uses both array and object notation for the stints.
- The initial data load will typically use array notation while subsequent updates use object notation


### Data mining query
Query to investigate message content

Austrian GP
```sql
SELECT *
--category, count(*)
FROM public.live_timing_messages
where 
category IN ('TrackStatus')
--and 
--is_streaming = true
and 
created_timestamp > '2026-06-28 11:50:00'
and 
created_timestamp < '2026-06-28 15:10:00'
--group by category
order by 1 desc
limit 5000
```

Silverstone GP
```sql
SELECT *
--category, count(*)
FROM public.live_timing_messages
where 
category IN ('TrackStatus')
--and 
--is_streaming = true
and 
created_timestamp > '2026-07-05 13:10:00'
and 
created_timestamp < '2026-07-05 16:20:00'
--group by category
order by 1 desc
limit 5000
```
