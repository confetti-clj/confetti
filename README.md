# confetti

A work-in-progress tool to help authoring static sites with AWS.

## AWS Docs

The gist of AWS' [Website Hosting Intro](http://docs.aws.amazon.com/gettingstarted/latest/swh/website-hosting-intro.html):

### Neccessary Checks

- does a bucket with this name exist?
- does a user with this name exist?
- does the user already have a policy with the same name?

### S3

- [x] Create buckets
   - [x] example.com
   - [x] www.example.com
   - [x] (optional) logs.example.com
- [] Add bucket policy to root bucket
- [] (optional) Enable logging for root bucket
   - specify logs.example.com as target bucket
- [x] Enable static website hosting
   - [x] **Index Document** index.html
   - [x] **Error Document** error.html
- [x] Redirect www bucket traffic to root bucket endpoint

### Route 53

1. Create a hosted zone for domain
2. Create two record sets for hosted zone
   - for root domain
     - Type: A - IPv4 address
     - Alias: Yes
     - Alias Target: S3 root website endpoint
     - Routing Policy: Simple
     - Evaluate Target Health: No
   - for the www subdomain
     - Name: www.
     - Type: A - IPv4 address
     - Alias: Yes
     - Alias Target: S3 www website endpoint

### Cloudfront

1. Create Distribution
   - Type: Web
   - Origin Domain Name: S3 website endpoint of root bucket
   - Origin ID: (filled in automatically)
   - Default Cache Behaviour Settings: default (?)
   - Distribution Settings:
     - Price class: All locations
     - Alternate Domain names: example.com, www.example.com
     - Default Root Object: index.html
     - (optional) Logging: On
     - (optional) Bucket for logs: logs.example.com
     - (optional) Log prefix: cdn/

### Route 53

1. Update hosted zone
   - A record for www domain
     - Alias Target: *cloudfront.net
   - A record for root domain
     - Alias Target: *cloudfront.net
