# Project Roadmap

## 🚀 High Priority
- [x] <!-- TODO: --> Have "inactive" session status mark the start of a new session
- [x] <!-- TODO: --> Capture driver list w/names
- [ ] <!-- TODO: --> TimingAppData with stint array/object notation handling
- [x] <!-- TODO: --> Different filter for livetiming to SSE vs routing
- [x] <!-- TODO: --> State change w/ from - to states

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
