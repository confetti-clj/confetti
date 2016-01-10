![confetti-logo-small](https://cloud.githubusercontent.com/assets/97496/11431670/0ef1bb58-949d-11e5-83f7-d07cf1dd89c7.png)

**(alpha)** A tool to help authoring static sites with Amazon Web Services (AWS).

**Rationale**: Static sites are fun. Deploying to S3 is pure
  joy. CloudFront makes scaling something you don't think about
  anymore. No servers to administrate no headaches to kill with aspirin.
  Setting it all up however is not as straightforward. Confetti is an attempt
  at encoding best practices into a repeatable program using [CloudFormation][cf]
  and providing handy tools for basic as well as advanced deployment scenarios. 

## Features

- Create all AWS resources required for ideal deployment of static sites
  - S3 Bucket, Bucket Policy, Cloudfront Distribution
- Provide a sepatarate user & access key that can only push to the bucket
- *(optional)* Setup DNS via Route 53
- Follow [AWS best practices for deploying static sites][aws-website-hosting].

## Usage

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
> remotely and won't be interrupted. However if you kill it you will
> have to get the information like bucket name, access key, etc
> manually so it's recommended to just leave it running.

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
`secret-key` values from the *.edn* file:

```
boot -d confetti sync-bucket --bucket "my-app-com-sitebucket-3fu0w0729ndk" \
                             --access-key AAA --secret-key BBB --dir my-app-site
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

### Getting Help

To get help on the command line you can always run:

```
boot create-site --help
boot sync-bucket --help
```

Also feel free to open issues to ask questions or suggest improvements.

## Appendix

#### Future Improvements

- When creating a static site on a subdomain of a domain that is already managed via Route53
  it would make sense to add the Record Set to the existing Hosted Zone. ([#17](https://github.com/confetti-clj/confetti/issues/17))
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
