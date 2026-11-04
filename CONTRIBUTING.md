# Contributing

Small, reviewable changes land fastest. A 20-line fix touching one file will merge
before a 2000-line refactor, even when the refactor is the better idea.

## Before opening a pull request

1. Reproduce on the latest commit — not on a release from six months ago.
2. If the change is more than a typo, open an issue first. I would rather agree on
   the shape with you than review an evening of work I cannot take.
3. Branch from `main`. Name the branch after the change, not after yourself.

## Style

- Match the code around you, even where you would have chosen differently.
- Do not reformat files you are not changing. It wrecks `git blame` and hides the
  real diff.
- Comments explain *why*. Never *what*.
- Add a regression test when the repo has a place for one. When it does not, say in
  the PR how you checked it by hand instead of leaving the section blank.

## Checklist

- [ ] The project builds (`npm run check`, `./gradlew build`, `pnpm build` — whatever applies)
- [ ] The diff is scoped to one concern
- [ ] Docs updated when behaviour or configuration changed

## What to expect

I am a student and this is unpaid work, so a reply can take a few days. I will not
leave you guessing: if I am not going to merge it, I will tell you and say why.
