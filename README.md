# confetti

A work-in-progress tool to help authoring static sites with AWS.

## AWS Docs

The gist of AWS' [Website Hosting Intro](http://docs.aws.amazon.com/gettingstarted/latest/swh/website-hosting-intro.html):

### Neccessary Checks

- does a bucket with this name exist?
- does a user with this name exist?
- does the user already have a policy with the same name?

- [ ] resource groups could be used to bundle everything - does not currently support cloudfront
- [ ] [cloudformation](http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/Welcome.html) might be useful to generate blueprints, causing less imperative code

relevant discussion about cloudformation: https://groups.google.com/forum/#!topic/clojure/BNIdWCehEAI

### S3

- [x] Create buckets
   - [x] example.com
   - [x] www.example.com
   - [x] *optional* logs.example.com
- [x] Add bucket policy to root bucket
- [ ] *optional* Enable CORS for bucket
- [ ] *optional* Enable logging for root bucket
   - [ ] specify logs.example.com as target bucket
- [x] Enable static website hosting
   - [x] **Index Document** index.html
   - [x] **Error Document** error.html
- [x] Redirect www bucket traffic to root bucket endpoint

**Learnings**

- One bucket is enough (www. can be handled via CloudFront)
- Website hosting does not need to be enabled (handled via CloudFront)
  **potentially wrong:** (untested) https://forums.aws.amazon.com/message.jspa?messageID=681707
- Logging can be handled at S3 as well as CloudFront level
https://forums.aws.amazon.com/message.jspa?messageID=681413#681413

### Route 53

- [x] Create a hosted zone for domain
- [x] Create two record sets for hosted zone
   - for root domain
     - Type: A - IPv4 address
     - Alias: Yes
     - Alias Target: S3 root website endpoint; [Hosted Zone Ids](http://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region)
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
     - [x] Price class: All locations
     - [x] Alternate Domain names: example.com, www.example.com
     - [x] Default Root Object: index.html
     - *optional* Logging: On
     - *optional* Bucket for logs: logs.example.com
     - *optional* Log prefix: cdn/

### Route 53

1. Update hosted zone
   - A record for www domain
     - Alias Target: *cloudfront.net
   - A record for root domain
     - Alias Target: *cloudfront.net
2. Ensure domain uses AWS DNS servers
   as they are specified in the new hosted zone

### SSL (?)

- Free certificates available via letsencrypt.org
- Cloudfront lets you add custom certs at no charge (http://aws.amazon.com/de/about-aws/whats-new/2014/03/05/amazon-cloudront-announces-sni-custom-ssl/)
- Ruby ACME client https://lolware.net/2015/10/27/letsencrypt_go_live.html
- Java https://community.letsencrypt.org/t/third-party-library-java-client/633

# Website configuration required when using Cloudfront?

All documentation as well as CloudFormation examples mention that for hosting a website with S3 the bucket should be configured for static website hosting.

In my experience however this isn't required if Cloudfront is used to serve the bucket's content.
Cloudfront allows you to provide a Default Root Object as well as Error pages similarly to how they can be specified during Static Website Configuration for S3 buckets.

Is there any specific reason it should be enabled anyways?

Thanks
