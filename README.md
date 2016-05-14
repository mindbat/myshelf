# myshelf
Batteries-included twitter bot for managing your Goodreads library.

## Build Status
[![Build Status](https://travis-ci.org/mindbat/myshelf.svg?branch=master)](https://travis-ci.org/mindbat/myshelf)

## Using the Bot

You can run the bot either locally or via Heroku to use it to manage your own Goodreads shelves and get recommendations on what you should read next.

### Twitter Access

You'll need to give the bot access to your twitter account. Go to Twitter and create an application for it. You'll need four keys: the Consumer Key and Consumer Secret for the application, and your Access Token and Access Token Secret for the application to access your account.

These four values will need to be set in a few environment variables: TWITTER_APP_KEY, TWITTER_APP_SECRET, TWITTER_USER_TOKEN, TWITTER_USER_SECRET

### Goodreads Access

You'll also need to sign up for the Goodreads API. The application key and secret you get from them will need to be in two different environment variables: GOODREADS_KEY and GOODREADS_SECRET

### Additional Dependencies

The bot will also need access to rabbitmq. It'll assume rabbitmq is running locally, unless you give it a different connection uri in the CLOUDAMQP_URL environment variable.

The bot tracks some state in postgresql. It will assume postgresql is running locally, with a db named myshelf that it can access. You can specify a different connection (hostname, db name, etc) using the DATABASE_URL environment variable.

### Running the Migrations

Myshelf uses migratus to manage its db migrations. Once you've got the postgresql db created and accessible, you'll need to run the migrations so you have the right db schema.

The easiest way to run the migrations is using lein:

    lein migratus migrate

### Running the bot

With all the setup complete, you're ready to run the bot:

     java $JVM_OPTS -cp target/myshelf-standalone.jar clojure.main -m myshelf.twitter

### Bot Commands

Myshelf accepts two commands: add and rank.

To issue the add command, send a tweet that looks like:

     myshelf-bot: add <book-title> by <book-author>

This will trigger the bot to look up the book in Goodreads by the title and author you've given it, and then add that book to your to-read shelf.

If successful, the bot will reply with a tweet that looks like:

     @<your-twitter-handle>: Added <book-author> to to-read

To issue the rank command, send a tweet that looks like:

     myshelf-bot: rank <shelf-name>

This will trigger the bot to pull down all the books you have on the given shelf, and then rank them in order of which ones you should read first. It'll then tweet back with the titles of the top four:

     @<your-twitter-handle>: <title-1>
                             <title-2>
			                 <title-3>
			                 <title-4>

### Approving the Bot

The first time you run the bot and give it a command, it'll need to get approval from you to access your account in Goodreads.

It'll tweet back with a url for you to hit in your browser. Clicking on the link it tweets should be sufficient.

Once you've approved the bot, it'll store your access token for later use, so you won't have to do it again.

## Using the Library

The core myshelf nses for interacting with the Goodreads API are available in clojars as a separate library.

To use it, in your project.clj, add myshelf as a dependency:

     [myshelf "0.2.1"]

You'll need to sign up for the Goodreads API. This will give you a key and secret to use for accessing it.

With those in hand, build a consumer for the API:

     (require '[myshelf.auth :as myshelf])

     (def good-consumer (myshelf/get-consumer KEY SECRET))

Then build a request token and find the url you'll need to go to in your browser to approve the app's access to your data:

     (def request-token (myshelf/get-request-token good-consumer))
     (myshelf/find-approval-uri good-consumer request-token)

After loading the url in your browser, you can get an access token, which you'll need for the rest of the API functions:

      (def access-token (myshelf/get-access-token good-consumer request-token))

Now fetch your user id to make sure everything's working:

    (def user-id (myshelf/get-user-id good-consumer access-token))

Note: The access token you have for your user id is just a map, and can be re-used with different consumers. To spare yourself the hassle of having to re-approve your application every time you're testing or working on it, it's advisable to save your access token somewhere for later use.

## License

&copy; 2016 Ron Toland

Licensed under the MIT License. See LICENSE for details.
