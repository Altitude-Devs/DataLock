# DataLock
<p>A velocity plugin which provides a way for bukkit plugins to handle data syncing</p>

<p>Use by sending a message on your own plugin message channel. Add it to the DataLock config in the following format:<br>
	<code>pluginname:customname</code></p>

## Sub channels
<ol>
  <li>
		<code>try-lock</code>:<br>Attempt to lock a data entry it will send a boolean back on <code>try-lock-result</code>.
		If it can't be locked yet it will attempt to queue your lock call,
		if there is an existing call it will send a failure message with info on that lock on <code>queue-lock-failed</code>.
	</li>
  <li>
		<code>check-lock</code>: Check if a data entry is locked, it will send the result back as a boolean on <code>check-lock-result</code>
	</li>
	<li>
		<code>try-unlock</code>: Attempt to unlock a data entry it will send a boolean back on <code>try-lock-result</code>
    If succesfull this will check if there is a queued lock for this data entry and if so lock that for that server and notify it on
    <code>locked-queued-lock</code> with information on what the data was
	</li>
</ol>

## Data formats
<ol>
	<li><code>try-lock</code>/<code>check-lock</code>/<code>try-unlock</code>:
		<ul>
			<li>UTF: sub channel (the sub channel)</li>
			<li>UTF: data (data to lock/check/unlock)</li>
		</ul>
	</li>
  <li><code>try-lock-result</code>
		<ul>
    	<li>UTF: sub channel (the sub channel)
    	<li>boolean: success (if the lock/unlock succeeded or not or if the data was locked or not)</li>
    	<li>UTF: data (data that was attempted to be locked/checked/unlocked)</li>
		</ul>
	</li>
  <li><code>queue-lock-failed</code>
		<ul>
    	<li>UTF: sub channel (the sub channel)</li>
    	<li>UTF: data (data that was attempted to be locked/checked/unlocked)</li>
    	<li>UTF: server name (the servername that the current lock is from)</li>
		</ul>
	</li>
  <li><code>locked-queued-lock</code>
		<ul>
    	<li>UTF: sub channel (the sub channel)</li>
   		<li>UTF: data (data that was locked/checked/unlocked)</li>
		</ul>
	</li>
</ol>
