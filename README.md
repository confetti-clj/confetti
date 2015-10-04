# confetti

A work-in-progress tool to help authoring static sites with AWS.


# AWS Docs


The gist of AWS' [Website Hosting Intro](http://docs.aws.amazon.com/gettingstarted/latest/swh/website-hosting-intro.html):

### S3

1. Create buckets
   - example.com
   - www.example.com
   - (optional) logs.example.com
2. Add bucket policy to root bucket
3. (optional) Enable logging for root bucket
   - specify logs.example.com as target bucket
4. Enable static website hosting
   - **Index Document** index.html
   - **Error Document** error.html
5. Redirect subdomain traffic to root domain
   - Redirect all requests to root bucket

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
