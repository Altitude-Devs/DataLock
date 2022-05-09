# DataLock
A velocity plugin which provides a way for bukkit plugins to handle data syncing

Use by sending a message on your own plugin message channel. Add it to the DataLock config in the following format:
`pluginname:customname`

Possible sub channels to send data on are:
  try-lock: Attempt to lock a data entry it will send a boolean back on `try-lock-result`.
            If it can't be locked yet it will attempt to queue your lock call,
            if there is an existing call it will send a failure message with info on that lock on `queue-lock-failed`.
  check-lock: Check if a data entry is locked, it will send the result back as a boolean on `check-lock-result`
  try-unlock: Attempt to unlock a data entry it will send a boolean back on `try-lock-result`
              If succesfull this will check if there is a queued lock for this data entry and if so lock that for that server and notify it on
              `locked-queued-lock` with information on what the data was

data formats:
  `try-lock`/`check-lock`/`try-unlock`:
    UTF: sub channel (the sub channel)
    UTF: data (data to lock/check/unlock)
  `try-lock-result`
    UTF: sub channel (the sub channel)
    boolean: success (if the lock/unlock succeeded or not or if the data was locked or not)
    UTF: data (data that was attempted to be locked/checked/unlocked)
  `queue-lock-failed`
    UTF: sub channel (the sub channel)
    UTF: data (data that was attempted to be locked/checked/unlocked)
    UTF: server name (the servername that the current lock is from)
  `locked-queued-lock`
    UTF: sub channel (the sub channel)
    UTF: data (data that was locked/checked/unlocked)
