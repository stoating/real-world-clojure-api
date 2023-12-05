# real-world-clojure-api using vs code

following along the tutorial from:  
https://www.youtube.com/@andrey.fadeev  
https://github.com/andfadeev/real-world-clojure-api  
(go like, star and subscribe)  

hints:  

to start:  
  start nREPL (using jack-in with dev alias)  
  start docker (docker compose up -d)  
    alternatively, start postgres using docker container  
    this should start username@localhost.session.sql  
  start / restart component system  
    perform component REPL Reset (see .vscode settings) (for me, alt+i r)  

to test:  
  in REPL: in file to test, alt+t c (run tests in current namespace)  
  in cmd: clj -X:test (run all tests)  
  hint:  
    do not forget to save and resart components before running tests  