# kindle-patcher

trying to make a patching classloader for the kindle (more specifically the kindle paperwhite 2 (because thats what i had))


### usage

(probably don't use it, but)

1. jailbreak your kindle
2. copy the folders `/opt/amazon/ebooks/lib`, `/opt/amazon/ebooks/booklet`, and `/opt/amazon/ebooks/sdk` to `vendor/`
3. run gradle build
4. copy the compiled jar to `/opt/amazon/ebooks/lib`
5. edit `/opt/amazon/ebook/bin/init.xargs` and add `-istart <name of jar>` above the other `-istart` lines
6. copy patches to `/mnt/us/patcher/patches`
7. reboot and hope it works

