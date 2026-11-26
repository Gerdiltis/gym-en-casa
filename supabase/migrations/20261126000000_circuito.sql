-- v0.6.0: circuit sessions (one pass of every exercise per round, N rounds).
alter table public.workout_sessions add column if not exists format text not null default 'series' check (format in ('series', 'circuit'));
alter table public.workout_sessions add column if not exists rounds integer not null default 1 check (rounds between 1 and 20);

alter table public.routines add column if not exists format text not null default 'series' check (format in ('series', 'circuit'));
alter table public.routines add column if not exists rounds integer not null default 1 check (rounds between 1 and 20);
alter table public.routines add column if not exists round_rest_s integer not null default 0 check (round_rest_s between 0 and 600);
