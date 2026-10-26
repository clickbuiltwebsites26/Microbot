# Starts the locally built Microbot client (quest-automation-fixes) with the launcher's JVM settings.
$java = 'C:\Users\tyler\dev-tools\jdk\jdk-17.0.13+11\bin\javaw.exe'
Start-Process $java -ArgumentList @(
  '-javaagent:C:\Users\tyler\.microbot\hwid-agent-2.6.43.jar', '-Xms256m', '-Xmx2g', '-XX:+UseZGC',
  '-XX:SoftMaxHeapSize=1638m', '-XX:+ZUncommit', '-XX:ZUncommitDelay=30', '-XX:+UseStringDedup', '-Xss512k',
  '-XX:+IgnoreUnrecognizedVMOptions', '-jar', 'C:\Users\tyler\.microbot\dev\microbot-questfix.jar')
