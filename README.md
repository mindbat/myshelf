# myshelf
Grab-bag of functions for accessing goodreads via clojure

## Getting Started

You'll need to sign up for the Goodreads API. This will give you a key and secret to use for accessing it.

With those in hand, build a consumer for the API:

     (require '[myshelf.core :as myshelf])

     (def good-consumer (myshelf/get-consumer KEY SECRET))

Then build a request token and find the url you'll need to go to in your browser to approve the app's access to your data:

     (def request-token (myshelf/get-request-token good-consumer))
     (myshelf/find-approval-uri good-consumer request-token)

After loading the url in your browser, you can get an access token, which you'll need for the rest of the API functions:

      (def access-token (myshelf/get-access-token good-consumer request-token))

Now fetch your user id to make sure everything's working:

    (def user-id (myshelf/get-user-id good-consumer access-token))
