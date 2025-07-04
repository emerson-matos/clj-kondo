
(ns schema.defprotocol
  (:require [schema.core :as s]))

(s/defprotocol MyProtocol
  "Docstring"
  :extend-via-metadata true
  (^:always-validate method1 :- s/Int
    [this a :- s/Bool]
    [this a :- s/Any, b :- s/Str]
    "Method doc2 ")
  (^:never-validate method2 :- s/Int
    [this]
    "Method doc2"))
