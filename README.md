![confetti-logo-small](https://cloud.githubusercontent.com/assets/97496/11431670/0ef1bb58-949d-11e5-83f7-d07cf1dd89c7.png)

[usage](#usage) | [change log](#changes) | [appendix](#appendix)

**(alpha)** A tool to help authoring static sites with Amazon Web Services (AWS).

**Rationale**: Static sites are fun. Deploying to S3 is pure
  joy. CloudFront makes scaling something you don't think about
  anymore. No servers to administrate; no tears to cry.
  Setting it all up however is not as straightforward. Confetti is an attempt
  at encoding best practices into a repeatable program using [CloudFormation][cf]
  and providing handy tools for basic as well as advanced deployment scenarios.

[](dependency)
```clojure
[confetti/confetti "0.2.0"] ;; latest release
```
[](/dependency)

## Who is this for?

- People that want static sites on custom domains with free SSL and minimal hassle.
- People that want to host static sites under root domains (e.g. example.com).
- People that want to setup new static sites often without much manual work (takes 15min of mostly waiting w/ Confetti).
- People that want to effortlessly have multiple static sites under one domain (e.g. demo.example.com and example.com)
- People that want excellent distribution accross the globe using a leading CDN.
- People that want to be able to delete all resources related to a site with a single click.

## Features

- Create all AWS resources required for ideal deployment of static sites
  - S3 Bucket, Bucket Policy, Cloudfront Distribution
- All resources are created via a CloudFormation template, allowing
  - easy deletion if something went wrong
  - abort upon conflicting configration
- Provide a separate user & access key that can only push to the bucket
- Setup DNS via Route 53 *(optional)*
- Follow [AWS best practices for deploying static sites][aws-website-hosting].
- Efficient synchronization of files to S3.

## Usage

[creating a site](#creating-a-site) | [syncing your site](#syncing-your-site) | [final step: dns](#final-step-dns) | [adding subdomains](#adding-subdomains)

> Confetti is packaged up as a [boot][boot] task. This is mainly because
> boot makes it easy to write commandline apps in Clojure without needing
> to worry about bootstrapping or dependency resolution.

Confetti provides two commands, in Boot-lingo called *tasks*. The `create-site` task
will create a [CloudFormation][cf] stack with all resources for your static website
and save all important information to an [EDN](edn) file in the current directory.

Let's go through an example of creating a site and syncing it for the first time.

### Creating a site

Let's say you want to deploy a site at `my-app.com`. To create an S3 bucket, a CloudFront
distribution and restricted access keys you can run the following:

```
boot -d confetti create-site --domain "my-app.com" --access-key XXX --secret-key YYY
```
> Note: the `-d confetti` bit makes sure Boot will download confetti so the `create-site` task will be available.

**Exception!** Because you want to use a naked/APEX domain you have to use Route53
for DNS. (You can find more on this in the Appendix.) Try again with DNS enabled:

```
boot -d confetti create-site --domain "my-app.com" --access-key XXX --secret-key YYY --dns
```

This should kick of the process. The first feedback should be appearing on your screen.
At some point no new events will get printed but the process also hasn't returned yet.
What you're waiting for now is the creation of your CloudFront
distribution. This usually takes between 10-15min.

> You may kill the process at this point. Everything is running
> remotely and won't be interrupted. A `.confetti.edn` file is saved
> in your current working directory and if using the `fetch-outputs`
> task with sufficient credentials you can download all useful
> information at any point in time. (The task will tell you if the
> stack isn't ready yet as well.)

![Confetti create-site progress](https://cloud.githubusercontent.com/assets/97496/12223984/2dec1b0e-b7e6-11e5-889c-2ea7a4af0fec.png)

After the `create-site` task finishes you should find a file in your
current working directory: `my-app-com.confetti.edn`. It's contents should
contain everything important about your newly provisioned resources:

```clojure
{:stack-id "arn:aws:cloudformation:us-east-1:297681564547:stack/my-app-com/xxx",
 :bucket-name "my-app-com-sitebucket-3fu0w0729ndk",
 :cloudfront-id "E3760XUWU2V9R7",
 :cloudfront-url "d3up0oy7r2svli.cloudfront.net",
 :access-key "AAA",
 :secret-key "BBB",
 :website-url "http://my-app.com",
 :hosted-zone-id "Z3KJWNUJTT8GHO"}
```

Now everything is ready for the first deployment!

### Syncing your site

Now the `sync-bucket` task comes into play. While the task provides
many different ways to specify what to upload we will just show the
simplest here: syncing a local directory. For our demo purposes lets
create a directory quickly:

```
mkdir my-app-site
echo "Hello World" > my-app-site/index.html
echo "About Us" > my-app-site/about.html
```

Now lets sync it. Take the `bucket-name`, `access-key` and
`secret-key` values from the *.confetti.edn* file:

```
boot -d confetti sync-bucket --bucket "my-app-com-sitebucket-3fu0w0729ndk" \
                             --access-key AAA --secret-key BBB --dir my-app-site
;; or alternatively
boot -d confetti sync-bucket --confetti-edn your-site.confetti.edn --dir my-app-site
```

This will upload `index.html` and `about.html` to your bucket. To
verify that everything was successful you can navigate to the URL
stored as `cloudfront-url` in the edn file.

> There are many more ways to specify what files to upload (with
> custom metadata if wanted) which are not covered by this guide.
> Consult `boot sync-bucket --help` for details.

### Final Step: DNS

Now the only step missing is properly setting up DNS. What needs to be
done here varies depending on whether you enabled the `--dns` option
or not. In the example above we enabled it so lets cover that case first:

**DNS with Route53:** Because you have a root/naked/apex domain setup
you decided to use manged DNS by AWS. Now you need to set the nameservers
for the domain you used to AWS' nameservers. These are different for different
Hosted Zones so you need to look them up in the [AWS Console](hosted-zones-admin).

**Without Route53:** When not using Route53 the only thing you have to
do is to add a CNAME entry to the Zonefile of your domain that points
to the Cloudfront distribution.

> Both of these steps will vary from domain registrar to domain
> registrar so it's recommended to check their individual
> documentation.

### Adding Subdomains

Let's say you used Confetti to create a site `weloveparens.com` and
now want to add a static site to a subdomain of that domain. You can just run:

```
boot create-site --domain "shop.weloveparens.com" --dns --access-key FOO --secret-key BAR
```

This will create a Route53 RecordSet in the HostedZone which has
previously been created for you when setting up `weloveparens.com`.
The S3 bucket, CloudFront distribution and so on will be created as usual.
Also as always everything (including the RecordSet) will be created as
a CloudFormation stack so if you no longer need it you can just delete
the stack, leaving `weloveparens.com` unaffected.

## Getting Help

To get help on the command line you can always run:

```
boot create-site --help
boot fetch-outputs --help
boot sync-bucket --help
```

Also feel free to open issues to ask questions or suggest improvements.

## Changes

#### 0.2.0

- Upgrade to `[confetti/cloudformation "0.1.6"]`, which brings the following improvements:
  - Enable compression by default
  - Fix some misconfiguration of the origin that caused problems when enabling SSL

#### 0.1.5

- bump [`confetti/s3-deploy`](https://github.com/confetti-clj/s3-deploy) to improve Windows compatibility

#### 0.1.5-alpha

- **HostedZone Reuse:** Creating a new HostedZone for each site has two drawbacks:
  - Each HostedZone costs 50 cent
  - Each HostedZone has a distinct set of nameservers that you'd need to supply to your domain provider

  By using one HostedZone for your root domain `example.com` these problems are solved and
  adding a new site at `demo.example.com` is just a matter of adding a RecordSet.
  **Confetti now tries to find an existing HostedZone and only adds a RecordSet if it finds one.**
- New `invalidation-paths` option for the `sync-bucket` task. Previously invalidation paths
  were determined based on the files you uploaded. Now you can provide a custom set. ([#21](https://github.com/confetti-clj/confetti/issues/21) + [#29](https://github.com/confetti-clj/confetti/pull/29))
- Fix bug with new method of supplying options via a `.confetti.edn` file

#### 0.1.4

- When users supply the `confetti-edn` option, we now accept both versions
  (ending with `.confetti.edn` and just the part before that). Previously
  it was expected that you only supply the part before the `.confetti.edn` suffix
- Adapt Readme to suggest usage of `confetti-edn` option and `fetch-outputs` task
- Refactor reporting into separate task that is called from `create-site`
- Give progress-reporting a hard limit of 16min. Previously the error reporting often
  got stuck preventing the entire process from returning. This should not happen anymore.
- Provide copy-able command to fetch outputs as part of progress reporting
- Move APEX domain info into `fetch-outputs` task
- Upgrade to `[confetti/cloudformation "0.1.3"]` to have `:website-url` in stack outputs
  no matter if Route53 is used or not
- Print time when starting progress reporting
- Print Cloudfront URL in `fetch-outputs` if Route53 isn't used

#### 0.1.3

- A `fetch-outputs` task has been added that can be used to download
  outputs of Cloudformation stacks. Previously the reporting often got stuck
  and didn't save stack outputs properly.
  To circumvent this you may now cancel the reporting and call `fetch-outputs`
  at any later point in time to download the outputs.
- The `sync-bucket` task now provides a `confetti-edn` option that can be used
  to supply the some-id part of a `{some-id}.confetti.edn`. The information
  in that file will then be used for instead of the regular task options.
- General improvements around error handling and option validation.

## Appendix

#### Additional Tweaks

##### Add SSL:

1. Get an SSL Cert using AWS Certificate Manager (ACM)
1. Configure Cloudfront Distribution to use newly issued certificate (i.e. "Custom SSL certificate")
1. Switch **Behavior** Viewer Protocol Policy to "Redirect HTTP to HTTPS"

👉 If anything is not working as expected, please open an issue. 👈

> **Note** If you end up getting 504 errors when requesting assets
> from your Cloudfront distribution double check you're really using
> the website endpoint as origin. The Origin Protocol policy **must
> be** "HTTP Only" as a result of using the website endpoint.

##### Enable Gzipping

- **v0.1.6 and up now take care of this automatically**
- Edit **Behavior**, set "Compress Objects Automatically" to "Yes"

#### Future Improvements

- In the future Confetti could and should support SSL as well.
  [Let's Encrypt][lets-encrypt] is no longer in beta and as soon as there is
  a usable Clojure/Java client it would be nice to make it "dead-simple" to
  deploy static sites with SSL.

#### APEX Domains

Cloudfront supports APEX domains but only if you use Route53's `ALIAS`
records. More information can be found in the
[official announcement][apex-support].

This limitation makes it harder to automate root (APEX) domain setups
thus it's currently not supported to create sites for root domains
without also managing DNS with Route53.

[boot]: https://github.com/boot-clj/boot
[lets-encrypt]: https://letsencrypt.org/
[edn]: https://github.com/edn-format/edn
[cf]: https://aws.amazon.com/cloudformation/
[apex-support]: https://aws.amazon.com/de/about-aws/whats-new/2013/06/11/announcing-custom-ssl-certificates-and-zone-apex-support-for-cloudfront/
[hosted-zones-admin]: https://console.aws.amazon.com/route53/home?region=us-east-1#hosted-zones
[aws-website-hosting]: http://docs.aws.amazon.com/gettingstarted/latest/swh/website-hosting-intro.html
