# magnito [![Build Status](https://travis-ci.org/mt0erfztxt/magnito.svg?branch=master)](https://travis-ci.org/mt0erfztxt/magnito)
A small DSL that generates SQL query to eagerly load entities, with any level of nesting, from DB.
**NOTE:** Currently supports only JSONB-based setup (PostgreSQL).

## Usage
Write [resource](https://github.com/mt0erfztxt/magnito#resource-description), pass it to `magnito.core/resource->sql` and get SQL string/vector back.

## Sample
Input
```clj
(def resource
  {:resourceType "Account"
   :references
   {:profile {:resourceType "Profile"}
    :posts
    {:resourceType "Post"
     :by [:author :id]
     :collection true
     :reverse true
     :references
     {:commentaries
      {:resourceType "Commentary"
       :by [:post :id]
       :collection true
       :reverse true
       :references
       {:author
        {:resourceType "Account"}}}}}}})
```

Output as string
```sql
WITH _account AS
(
  SELECT t1.resource AS account FROM Account t1
),
_account_profile AS
(
  SELECT t2.resource AS profile
  FROM _account t1
    LEFT JOIN Profile t2 ON jsonb_extract_path_text (t1.account,'profile','id') = jsonb_extract_path_text (t2.resource,'id')
),
_account_posts AS
(
  SELECT t2.resource AS posts
  FROM _account t1
    LEFT JOIN Post t2 ON jsonb_extract_path_text (t1.account,'id') = jsonb_extract_path_text (t2.resource,'author','id')
),
_account_posts_commentaries AS
(
  SELECT t2.resource AS commentaries
  FROM _account_posts t1
    LEFT JOIN Commentary t2 ON jsonb_extract_path_text (t1.posts,'id') = jsonb_extract_path_text (t2.resource,'post','id')
),
_account_posts_commentaries_author AS
(
  SELECT t2.resource AS author
  FROM _account_posts_commentaries t1
    LEFT JOIN Account t2 ON jsonb_extract_path_text (t1.commentaries,'author','id') = jsonb_extract_path_text (t2.resource,'id')
)
SELECT JSONB_AGG(t1.account) AS result
FROM (SELECT CASE
               WHEN t2.posts IS NULL THEN t1.account
               ELSE JSONB_SET(t1.account,'{posts}',t2.posts)
             END AS account
      FROM (SELECT CASE
                     WHEN t2.profile IS NULL THEN t1.account
                     ELSE JSONB_SET(t1.account,'{profile}',t2.profile)
                   END AS account
            FROM _account t1
              LEFT JOIN _account_profile t2 ON jsonb_extract_path_text (t1.account,'profile','id') = jsonb_extract_path_text (t2.profile,'id')) t1
        LEFT JOIN (SELECT JSONB_AGG(t1.posts) AS posts
                   FROM (SELECT DISTINCT t1.posts AS posts,
                                jsonb_extract_path_text(t1.posts,'id')
                         FROM (SELECT CASE
                                        WHEN t2.commentaries IS NULL THEN t1.posts
                                        ELSE JSONB_SET(t1.posts,'{commentaries}',t2.commentaries)
                                      END AS posts
                               FROM _account_posts t1
                                 LEFT JOIN (SELECT JSONB_AGG(t1.commentaries) AS commentaries
                                            FROM (SELECT DISTINCT t1.commentaries AS commentaries,
                                                         jsonb_extract_path_text(t1.commentaries,'id')
                                                  FROM (SELECT CASE
                                                                 WHEN t2.author IS NULL THEN t1.commentaries
                                                                 ELSE JSONB_SET(t1.commentaries,'{author}',t2.author)
                                                               END AS commentaries
                                                        FROM _account_posts_commentaries t1
                                                          LEFT JOIN _account_posts_commentaries_author t2 ON jsonb_extract_path_text (t1.commentaries,'author','id') = jsonb_extract_path_text (t2.author,'id')) t1
                                                  ORDER BY jsonb_extract_path_text(t1.commentaries,'id')) t1
                                            GROUP BY jsonb_extract_path_text(t1.commentaries,'post','id')
                                            HAVING jsonb_extract_path_text (t1.commentaries,'post','id') IS NOT NULL) t2 ON jsonb_extract_path_text (t1.posts,'id') = jsonb_extract_path_text (t2.commentaries,'0','post','id')) t1
                         ORDER BY jsonb_extract_path_text(t1.posts,'id')) t1
                   GROUP BY jsonb_extract_path_text(t1.posts,'author','id')
                   HAVING jsonb_extract_path_text (t1.posts,'author','id') IS NOT NULL) t2 ON jsonb_extract_path_text (t1.account,'id') = jsonb_extract_path_text (t2.posts,'0','author','id')) t1
```

## Demo
In shell (must support `export`, see `.env` file in project root for details)
```
source .env
docker-compose up -d
lein repl
```
then in REPL
```
(require '[magnito.demo :as demo])
(demo/run)
```

## API
### resource->sql
Returns SQL query, that can be a string or a vector depending on passed in `options`.
```clj
(resource->sql resource)
(resource->sql resource options)
```

#### Arguments
* `resource` - required, map. Resource map, see [resource](https://github.com/mt0erfztxt/magnito#resource-description) for details.
* `options` - optional, map:
  - `:separate?` - boolean, no default. When evaluates to logical true then executing generated SQL query would return a seq of maps where each map would have only single key-value pair - key is a keyword obtained by stringifying and lower casing value of `:resourceType` of resource root, and value is a map - one row of result of executing generated SQL query. Otherwise generated SQL query would be wrapped so executing it a seq of single map would be returned and that map would have single key-value pair in turn, where key would be `:result` and value is a seq of maps where each map is a value of JSONB field. For example:
  ```clj
  (def sample-resource
    {:resourceType "Account"})

  (jdbc/query db-spec
              (resource->sql sample-resource))
  ;; => ({:result ({:id "account-1"} {:id "account-2"})})

  (jdbc/query db-spec
              (resource->sql sample-resource {:separate? true}))
  ;; => ({:account {:id "account-1"}} {:account {:id "account-2"}})
  ```
  - `:str?` - boolean, no default. When evaluates to logical true then SQL query would be returned as string, otherwise vector of query with placeholders and parameters would be returned.
  
## Resource description
Resource is a map that must/may have following key-value pairs (in no particular order):
* `:resourceType` - required, string. Specifies name of DB table that holds entities of interest.
* `:references` - optional, map. Each key of map is a keyword that specifies attribute under which referenced entity would be attached to parent and value is a resource map.
* `:collection` - optional, boolean, default to `false`. When evaluates to logical true then specified resource is a collection. Collection resources may not have `:reverse` key-value pair set to logical false. Ignored for root resource.
* `:reverse` - optional, boolean, default to `false`. When evaluates to logical true then resource points to parent resource itself, otherwise parent resource have reference to referenced resource. Ignored for root resource. For example, for resource
```clj
{:resourceType "Accout"
 :references
 {:profile
  {:resourceType "Profile" :reverse false}}}
```
JSON for `Account` must have corresponding attribute
```json
{"profile": {"id": "profile-1"}}
```
otherwise JSON for `Profile` must have corresponding attribute
```json
{"account": {"id": "account-1"}}
```
* `:id` - optional, keyword, `:id` by default. Allows to specify id attribute used in JSON.
* `:by` - optional, seq of keywords. Ignored for root resource. When not set would be a 2-elements vector of keywordized parent resource's type and id when `:reverse` evaluates to logical true or 2-elements vector of referenced resource's key and id when `:reverse` evaluates to logical false. For example
```clj
{:resourceType "Accout"
 :references
 {:profile
  {:resourceType "Profile"
   :reverse false   ; Means that Account JSON have {"profile": {"id": "profile-1"}} to point to Profile JSON.
   ;; by [:info :id]   ; Means that Account JSON have {"info": {"id": "profile-1"}} to point to Profile JSON.
   }}}

{:resourceType "Accout"
 :references
 {:profile
  {:resourceType "Profile"
   :reverse true   ; Means that Profile JSON have {"account": {"id": "account-1"}} to point to Account JSON.
   ;; by [:user :id]   ; Means that Profile JSON have {"user": {"id": "account-1"}} to point to Account JSON.
   }}}
```

## Development
In shell (must support `export`, see `.env` file in project root for details)
```
source .env
docker-compose up -d
lein repl
```

## Testing
In shell (must support `export`, see `.env` file in project root for details)
```
source .env
docker-compose up -d
lein test
```

## Credits
Original idea of DSL by [niquola](https://github.com/niquola).

## TODO
* Document code.
* Simplify and clean code.
* Allow to use WHERE in root resource.
* Allow to specify subset of entity attributes that must be in result - currently JSONB field content returned as-is.
* Allow to use LIMIT in resources.
* Allow to specify name of JSONB field - currently `resource` is used everywhere.
* Check whether idea can be expanded to 'traditional' (non JSONB-based) entities or not.

## License

Copyright © 2018 mt0erfztxt

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
