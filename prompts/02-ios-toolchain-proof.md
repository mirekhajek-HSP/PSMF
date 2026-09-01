# Prompt — iOS toolchain proof

> **SUPERSEDED by `07-ios-toolchain-proof.md`** (2026-09-01). That prompt runs
> against the real repository rather than a throwaway template, because the
> app now carries five unverified iOS `actual` files. Kept for its Part 1,
> which 07 reuses.

**Where:** MacBook Pro 2018 · **Model:** Opus

This is the one investigation that could invalidate the platform decision, so run
it early. It deliberately uses a **throwaway KMP template, not the real repo** —
proving the toolchain should not wait on getting code onto the Mac.

---

```
I need to find out whether this Mac can build and ship iOS apps, before I invest
weeks of work assuming it can. This is a toolchain investigation, not a project.

## The machine

MacBook Pro 2018 (Intel). I do not know what macOS it is running or whether it can
run a current Xcode. That is exactly what I need you to establish.

## The chain I need verified, in order

1. What macOS version is this Mac on, and what is the newest it can run?
   Apple has been retiring Intel Macs and I believe recent macOS dropped the 2018
   MacBook Pro. Confirm rather than assume.

2. What Xcode version can that macOS run?

3. What Xcode / iOS SDK version does Apple currently REQUIRE for App Store
   submissions? Check Apple's current developer documentation. Do not answer from
   memory — this changes and a stale answer is worse than none.

4. Does (2) satisfy (3)? This is the actual question. If it does not, this Mac can
   develop but cannot submit, and I need to know that now rather than in December.

## Then prove it works end to end

Generate a STOCK Kotlin Multiplatform + Compose Multiplatform template — the
JetBrains web wizard or the current IntelliJ template. Deliberately NOT my real
project: I want the toolchain proven independently of my code.

Get it to:
  a. build for the iOS simulator
  b. run on the simulator
  c. build for a physical iPhone and run on it — I will connect one and I have an
     Apple ID; tell me what to do at each step

Record how long a CLEAN build takes. Kotlin/Native compilation is slow on Intel and
I need to know what I am signing up for on every release.

## Also tell me

- Whether an Apple Developer Program account already exists that this Mac can use.
  The company already ships two apps on the App Store, so an organisation account
  almost certainly exists — I need to know how to get this Mac onto it.
- What TestFlight distribution would look like from here. This matters: piloting
  with a handful of Prague referees is exactly TestFlight's use case.
- Whether GitHub Actions macOS runners would be a viable release path if this Mac
  turns out too slow or too old to submit. This is my fallback and I need to know
  whether it is real.

## Do not

- Write any product code.
- Install anything heavyweight without telling me what and why first.
- Assume the answer to step 4 is fine. It may not be, and a confident wrong answer
  here costs me weeks.

## Report back

The four-step chain with actual version numbers, the clean-build time, and a plain
verdict: can this Mac develop iOS, can it submit to the App Store, or neither.
```
