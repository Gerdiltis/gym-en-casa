-- v0.2.0: fitball and long resistance bands, pregnancy mode and labour-preparation goal.

insert into public.equipment (slug, name) values
  ('pelota', 'Fitball (55–65 cm)'),
  ('bandas_largas', 'Bandas elásticas largas (kit 10–40 kg)')
on conflict (slug) do update set name = excluded.name;

update public.equipment set name = 'Gomas elásticas finas / mini bands' where slug = 'gomas';

-- Pregnancy suitability: 'apto' (safe), 'parto' (labour preparation), null = not recommended in pregnancy.
alter table public.exercises add column if not exists pregnancy text check (pregnancy in ('apto', 'parto'));

-- Pregnancy mode on the profile; labour preparation is a valid goal.
alter table public.profiles add column if not exists pregnant boolean not null default false;
alter table public.profiles drop constraint if exists profiles_goal_check;
alter table public.profiles add constraint profiles_goal_check
  check (goal in ('fuerza', 'cardio', 'tonificar', 'movilidad', 'parto'));
