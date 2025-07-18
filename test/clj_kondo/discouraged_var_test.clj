(ns clj-kondo.discouraged-var-test
  (:require
   [clj-kondo.test-utils :as tu :refer [lint! assert-submaps assert-submaps2]]
   [clojure.test :as t :refer [deftest is testing]]))

(deftest discouraged-var-test
  (assert-submaps
   '({:file "<stdin>", :row 1, :col 15, :level :warning, :message "Too slow"})
   (lint! "(defn foo [x] (satisfies? Datafy x))"
          '{:linters  {:discouraged-var {clojure.core/satisfies? {:message "Too slow"}}}}))
  (assert-submaps
   '({:file "<stdin>", :row 1, :col 15, :level :warning, :message "Too slow"})
   (lint! "(defn foo [x] (satisfies? Datafy x))"
          '{:ns-groups [{:pattern "(cljs|clojure).core" :name core}]
            :linters  {:discouraged-var {core/satisfies? {:message "Too slow"}}}}))
  (assert-submaps
   '({:file "<stdin>", :row 1, :col 34, :level :warning, :message "Closed source"})
   (lint! "(require '[closed.source :as s]) (s/fn)"
          '{:linters  {:discouraged-var {closed.source/fn {:message "Closed source"}}}}))
  (assert-submaps
   '()
   (lint! "(require '[closed.source :as s]) (comment (s/fn))"
          '{:linters  {:discouraged-var {closed.source/fn {:message "Closed source"}}}
            :config-in-comment
            {:linters {:discouraged-var {:level :off}}}}))
  (is (empty?
       (lint!
        (str "(ns foo {:clj-kondo/config {:linters {:discouraged-var {:level :off}}}})\n"
             "(satisfies? Datafy 5)")
        '{:linters  {:discouraged-var {clojure.core/satisfies? {:message "Too slow"}}}})))
  (is (empty?
       (lint!
        (str "(ns foo {:clj-kondo/config '{:linters {:discouraged-var {clojure.core/satisfies? {:level :off}}}}})\n"
             "(satisfies? Datafy 5)")
        '{:linters {:discouraged-var {clojure.core/satisfies? {:message "Too slow"}}}})))
  (is (empty?
       (lint!
        (str "(ns foo)\n"
             "(satisfies? Datafy 5)")
        '{:linters {:discouraged-var {clojure.core/satisfies? {:message "Too slow"}}}
          :config-in-ns {foo {:linters {:discouraged-var {clojure.core/satisfies? {:level :off}}}}}}))))

(deftest namespace-matches-multiple-ns-groups-test
  (testing "merged config in x.core namespace"
    (let [test-fn (fn [conf]
                    (assert-submaps2
                     '({:file "<stdin>", :row 3, :col 3, :level :warning, :message "Please don't eval stuff"}
                       {:file "<stdin>", :row 4, :col 3, :level :warning, :message "Use log"}
                       {:file "<stdin>", :row 5, :col 3, :level :warning, :message "Use logf"})
                     (lint! "(ns x.core)
(defn x []
  (eval \"WOW\")
  (println \"OK\")
  (printf \"NICE\n\"))"
                            conf)))
          ns-groups '[{:pattern "^x\\.core.*$"
                       :name    core-namespaces}
                      {:pattern "^x\\..*$"
                       :name    x-namespaces}]
          conf-fn (fn [ns-groups]
                    (tu/template
                     '{:linters
                       {:discouraged-var
                        {clojure.core/eval {:message "Please don't eval stuff"}}}

                       :ns-groups ::ns-groups
                       :config-in-ns
                       {core-namespaces
                        {:linters
                         {:discouraged-var
                          {clojure.core/println {:message "Use log"}}}}

                        x-namespaces
                        {:linters
                         {:discouraged-var
                          {clojure.core/printf {:message "Use logf"}}}}}}
                     {::ns-groups ns-groups}))]
      (test-fn (conf-fn ns-groups))
      (test-fn (conf-fn (reverse ns-groups)))))
  (testing "multiple matched discouraged vars via ns-groups (this behavior isn't specified and we might be overtesting here)"
    (assert-submaps
     '({:file "<stdin>", :row 1, :col 15, :level :warning, :message "Way too slow"}
       {:file "<stdin>", :row 1, :col 15, :level :warning, :message "Too slow"})
     (lint! "(defn foo [x] (satisfies? Datafy x))"
            '{:ns-groups [{:pattern "(cljs|clojure).core" :name core}]
              :linters  {:discouraged-var {core/satisfies? {:message "Too slow"}
                                           clojure.core/satisfies? {:message "Way too slow"}}}}))))
(deftest level-test
  (assert-submaps2
   [{:file "<stdin>",
     :row 1,
     :col 24,
     :level :error,
     :message "Discouraged var: foo/bar"}]
   (lint! "(ns foo) (defn bar []) (bar)"
          '{:linters {:discouraged-var {foo/bar {:level :error}}}})))

(deftest js-test
  (assert-submaps2
   '({:file "<stdin>", :row 1, :col 1, :level :warning, :message "Use web.http/js-fetch instead"}
     {:file "<stdin>", :row 2, :col 1, :level :warning, :message "Use web.http/js-fetch instead"}
     {:file "<stdin>", :row 3, :col 1, :level :warning, :message "Use web.http/js-fetch instead"})
   (lint! "js/fetch
(js/fetch)
(js/fetch.foo)"
          '{:linters {:discouraged-var {js/fetch {:message "Use web.http/js-fetch instead"}}}}
          "--lang" "cljs")))

(deftest arity-test
  (assert-submaps2
   [{:file "<stdin>",
     :row 1,
     :col 11,
     :level :warning,
     :message "Don't use lazy version of map"}
    {:file "<stdin>",
     :row 1,
     :col 29,
     :level :warning,
     :message "Don't use lazy version of map"}]
   (lint! "(map inc) (map inc [1 3 4]) (map vector [1 2 3] [1 2 3] [1 2 3] [1 2 3])"
          '{:linters  {:discouraged-var {clojure.core/map {:message "Don't use lazy version of map"
                                                           :arities #{2 :varargs}}}}})))

(deftest langs-test
  (let [config '{:ns-groups [{:name core :pattern "(clojure|cljs)\\.core"}]
                 :linters  {:discouraged-var {core/juxt {:message "I love juxt but don't use it in CLJS"
                                                         :langs #{:cljs}}}}}]
    (assert-submaps2
     [{:file "foo.cljs",
       :row 1,
       :col 1,
       :level :warning,
       :message "I love juxt but don't use it in CLJS"}
      {:file "foo.cljs",
       :row 1,
       :col 13,
       :level :warning,
       :message "I love juxt but don't use it in CLJS"}]
     (lint! "(juxt :foo) juxt" config "--filename" "foo.cljs"))
    (assert-submaps2
     []
     (lint! "(juxt :foo) juxt" config "--filename" "foo.clj"))
    (assert-submaps2
     [{:file "foo.cljc",
       :row 1,
       :col 1,
       :level :warning,
       :message "I love juxt but don't use it in CLJS"}
      {:file "foo.cljc",
       :row 1,
       :col 13,
       :level :warning,
       :message "I love juxt but don't use it in CLJS"}
      {:file "foo.cljc",
       :row 1,
       :col 37,
       :level :warning,
       :message "I love juxt but don't use it in CLJS"}]
     (lint! "(juxt :foo) juxt #?(:clj juxt :cljs juxt)" config "--filename" "foo.cljc"))))
