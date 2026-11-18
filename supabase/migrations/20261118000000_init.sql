-- Gym en Casa: initial schema. Every table has RLS; users only see their own rows.

-- ---------------------------------------------------------------- catalog (read-only for users)
create table public.equipment (
  slug text primary key,
  name text not null
);

insert into public.equipment (slug, name) values
  ('ninguno', 'Sin material'),
  ('gomas', 'Gomas elásticas'),
  ('esterilla', 'Esterilla'),
  ('mancuerna', 'Mancuerna de discos (hasta 10 kg)'),
  ('foam', 'Rodillo de foam'),
  ('silla', 'Silla'),
  ('pared', 'Pared'),
  ('escalon', 'Escalón'),
  ('sofa', 'Sofá'),
  ('toalla', 'Toalla'),
  ('mochila', 'Mochila'),
  ('botellas', 'Botellas de agua')
on conflict (slug) do nothing;

create table public.exercises (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  description text not null default '',
  muscle_groups text[] not null default '{}',
  -- every item the exercise needs; 'ninguno' = no equipment
  equipment text[] not null default '{ninguno}',
  type text not null check (type in ('fuerza', 'cardio', 'core', 'movilidad', 'recuperacion')),
  difficulty smallint not null default 1 check (difficulty between 1 and 3),
  unilateral boolean not null default false,
  video_path text,
  thumb_path text,
  created_at timestamptz not null default now()
);

alter table public.equipment enable row level security;
alter table public.exercises enable row level security;
create policy "catalog readable by signed-in users" on public.equipment for select to authenticated using (true);
create policy "catalog readable by signed-in users" on public.exercises for select to authenticated using (true);

-- ---------------------------------------------------------------- profiles
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  level text not null default 'intermedio' check (level in ('principiante', 'intermedio', 'avanzado')),
  goal text not null default 'tonificar' check (goal in ('fuerza', 'cardio', 'tonificar', 'movilidad')),
  equipment text[] not null default '{}',
  -- global rest between sets, applied to every workout
  rest_between_sets_s integer not null default 30 check (rest_between_sets_s between 10 and 120),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
create policy "own profile: select" on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy "own profile: insert" on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy "own profile: update" on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id) values (new.id) on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- workouts
create table public.workout_sessions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null default '',
  mode text not null check (mode in ('ai', 'manual')),
  goal text not null,
  duration_target_min integer not null,
  started_at timestamptz not null,
  finished_at timestamptz not null,
  completed boolean not null default true,
  created_at timestamptz not null default now()
);
create index workout_sessions_user_started on public.workout_sessions (user_id, started_at desc);

create table public.session_sets (
  session_id uuid not null references public.workout_sessions (id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  position integer not null,
  exercise_slug text not null,
  set_index integer not null,
  planned_s integer not null,
  skipped boolean not null default false,
  primary key (session_id, position)
);
create index session_sets_user on public.session_sets (user_id);

-- ---------------------------------------------------------------- routines
create table public.routines (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  goal text not null,
  duration_target_min integer not null,
  created_at timestamptz not null default now()
);
create index routines_user on public.routines (user_id);

-- No rest column: rest is always the user's global setting.
create table public.routine_items (
  routine_id uuid not null references public.routines (id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  position integer not null,
  exercise_slug text not null,
  sets integer not null check (sets between 1 and 10),
  work_s integer not null check (work_s between 5 and 600),
  primary key (routine_id, position)
);
create index routine_items_user on public.routine_items (user_id);

alter table public.workout_sessions enable row level security;
alter table public.session_sets enable row level security;
alter table public.routines enable row level security;
alter table public.routine_items enable row level security;

create policy "own rows" on public.workout_sessions for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.session_sets for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.routines for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.routine_items for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- ---------------------------------------------------------------- storage: public bucket for exercise media
insert into storage.buckets (id, name, public) values ('exercise-media', 'exercise-media', true)
on conflict (id) do nothing;
