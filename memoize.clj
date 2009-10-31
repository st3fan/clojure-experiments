
(defn expire-cached-results [cached-results time-to-live]
  "Expire items from the cached function results."
  (into {} (filter (fn [[k v]] (> time-to-live (- (System/currentTimeMillis) (:time v)))) cached-results)))

(defn my-memoize
  "Returns a memoized version of a referentially transparent function. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. Cached results are
  removed from the cache when their time to live value expires."
  [function time-to-live]
  (let [cached-results (atom {})]
    (fn [& arguments]
      (swap! cached-results expire-cached-results time-to-live)
      (if-let [entry (find @cached-results arguments)]
        (:result (val entry))
        (let [result (apply function arguments)]
          (swap! cached-results assoc arguments { :result result :time (System/currentTimeMillis)})
          result)))))

(defn calculation [n]
  (println "Doing some bistromath")
  (* n 42))

(def foo (my-memoize calculation 5000))

(def bar (my-memoize calculation 60000))

