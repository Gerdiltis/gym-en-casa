-- v0.5.0: free-form exercise tags (e.g. 'calistenia' = bodyweight, works with or without equipment).
alter table public.exercises add column if not exists tags text[] not null default '{}';
create index if not exists exercises_tags on public.exercises using gin (tags);
