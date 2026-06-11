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

```sql
SELECT *
--category, count(*)
FROM public.live_timing_messages
where 
category IN ('TrackStatus')
--and 
--is_streaming = true
and 
created_timestamp > '2026-03-29 04:00:00'
and 
created_timestamp < '2026-03-29 07:15:00'
--group by category
order by 1 desc
limit 5000
```

```sql
SELECT *
--category, count(*)
FROM public.live_timing_messages
where 
category IN ('TrackStatus')
--and 
--is_streaming = true
and 
created_timestamp > '2026-05-03 15:59:00'
and 
created_timestamp < '2026-05-03 19:30:00'
--group by category
order by 1 desc
limit 5000
```

```sql
SELECT *
--category, count(*)
FROM public.live_timing_messages
where 
category IN ('TrackStatus')
--and 
--is_streaming = true
and 
created_timestamp > '2026-05-24 18:50:00'
and 
created_timestamp < '2026-05-24 22:20:00'
--group by category
order by 1 desc
limit 5000
```
