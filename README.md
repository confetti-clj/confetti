![confetti-logo-small](https://cloud.githubusercontent.com/assets/97496/11431670/0ef1bb58-949d-11e5-83f7-d07cf1dd89c7.png)

**(alpha)** A tool to help authoring static sites with AWS.

## Features

- Create all AWS resources required for ideal deployment of static sites
  - S3 Bucket, Bucket Policy, Cloudfront Distribution
- Provide a sepatarate user & access key that can only push to the bucket
- *(optional)* Setup DNS via Route 53
- Follow [AWS best practices for deploying static sites](http://docs.aws.amazon.com/gettingstarted/latest/swh/website-hosting-intro.html).

## Usage

> Currently confetti is packaged up as a [boot][boot] task. This
> is mainly because boot makes it easy to write commandline apps in
> Clojure without needing to worry about bootstrapping or dependency
> resolution.

To get help on the command line you can run:

```
boot create-site --help
boot sync-bucket --help
```

An example to create a new stack:
```
boot create-site --domain "hello.martinklepsch.org"
```
An example to sync an S3 bucket:
```
boot sync-bucket --bucket "hello.martinklepsch.org"
```
**NOTE**: Both tasks require a `creds` option containing authorized AWS keys.

### APEX Domains

Cloudfront supports APEX domains but only if you use Route53's `ALIAS`
records. More information can be found in the
[official announcement](https://aws.amazon.com/de/about-aws/whats-new/2013/06/11/announcing-custom-ssl-certificates-and-zone-apex-support-for-cloudfront/).

This limitation makes it harder to automate root (APEX) domain
setups thus it's currently not supported to create sites for
root domains without also managing DNS with Route53.

### Edge cases

- subdomain setup with DNS but Zone for root domain exists
  *Should work fine for cost optimization these zones could be merged however.*

### SSL

In the future I'd like this tool to setup SSL as well.
[Let's Encrypt][lets-encrypt] is still in beta but as soon as this is
generally available and someone wrote a usable Clojure/Java client it
would be really nice to make it "dead-simeple" to deploy static sites
with SSL

- Free certificates available via letsencrypt.org
- Cloudfront lets you add custom certs [at no charge](http://aws.amazon.com/de/about-aws/whats-new/2014/03/05/amazon-cloudront-announces-sni-custom-ssl/)
- [Ruby ACME client](https://lolware.net/2015/10/27/letsencrypt_go_live.html)
- [Java ACME client](https://community.letsencrypt.org/t/third-party-library-java-client/633)

[boot]: https://github.com/boot-clj/boot
[lets-encrypt]: https://letsencrypt.org/
