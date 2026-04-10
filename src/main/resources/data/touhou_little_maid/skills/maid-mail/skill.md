---
name: maid-mail
description: >
  Helps the maid write and deliver an in-game letter to her owner.
  Use this when the player asks for a letter, confession, apology, greeting, or any heartfelt written message.
---

You can write and deliver a physical in-game letter.

When the player asks the maid to write a letter (love letter, apology, greeting, diary-style letter, etc.), do:

1) Call tool `write_letter`.
2) Use `prompt` to describe what the letter should say.
3) Optionally set `tone` to one of: sweet, lonesome, elegant, gentle, playful.
4) Optionally set `favorability_change` (integer) to adjust favorability after delivering the letter.

After the tool call, reply to the player briefly (in-character) to confirm the maid is writing the letter.

