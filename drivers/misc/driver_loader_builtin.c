#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/slab.h>

#define DRIVER_NAME "BYNb"
#define CLASS_NAME  "uevents_records"
#define IOCTL_MAGIC_CMD 0x803

static dev_t dev_num;
static struct cdev my_cdev;
static struct class *my_class = NULL;
static struct device *my_device = NULL;

static int dev_open(struct inode *inodep, struct file *filep) {
    return 0;
}

static int dev_release(struct inode *inodep, struct file *filep) {
    return 0;
}

static ssize_t dev_read(struct file *filep, char __user *buffer, size_t len, loff_t *offset) {
    return 0;
}

static ssize_t dev_write(struct file *filep, const char __user *buffer, size_t len, loff_t *offset) {
    return len;
}

static long dev_ioctl(struct file *filep, unsigned int cmd, unsigned long arg) {
    if (cmd == IOCTL_MAGIC_CMD) {
        /* Returns the magic base address 0x70000000 expected by AngryMod Driver Loader */
        unsigned long base_addr = 0x70000000;
        if (arg != 0) {
            if (copy_to_user((void __user *)arg, &base_addr, sizeof(base_addr))) {
                return -EFAULT;
            }
        }
        return 0x70000000;
    }
    return 0;
}

static const struct file_operations fops = {
    .owner          = THIS_MODULE,
    .open           = dev_open,
    .release        = dev_release,
    .read           = dev_read,
    .write          = dev_write,
    .unlocked_ioctl = dev_ioctl,
    .compat_ioctl   = dev_ioctl,
};

static int __init driver_loader_builtin_init(void) {
    int ret;
    pr_info("[BYNb] Initializing built-in RT Driver Loader for Begonia\n");

    ret = alloc_chrdev_region(&dev_num, 0, 1, DRIVER_NAME);
    if (ret < 0) {
        pr_err("[BYNb] alloc_chrdev_region failed: %d\n", ret);
        return ret;
    }

    cdev_init(&my_cdev, &fops);
    my_cdev.owner = THIS_MODULE;

    ret = cdev_add(&my_cdev, dev_num, 1);
    if (ret < 0) {
        unregister_chrdev_region(dev_num, 1);
        pr_err("[BYNb] cdev_add failed: %d\n", ret);
        return ret;
    }

    my_class = class_create(THIS_MODULE, CLASS_NAME);
    if (IS_ERR(my_class)) {
        cdev_del(&my_cdev);
        unregister_chrdev_region(dev_num, 1);
        pr_err("[BYNb] class_create failed\n");
        return PTR_ERR(my_class);
    }

    my_device = device_create(my_class, NULL, dev_num, NULL, DRIVER_NAME);
    if (IS_ERR(my_device)) {
        class_destroy(my_class);
        cdev_del(&my_cdev);
        unregister_chrdev_region(dev_num, 1);
        pr_err("[BYNb] device_create failed\n");
        return PTR_ERR(my_device);
    }

    pr_info("[BYNb] Driver device created at /dev/%s successfully! Magic IOCTL 0x803 ready\n", DRIVER_NAME);
    return 0;
}

static void __exit driver_loader_builtin_exit(void) {
    if (my_device) device_destroy(my_class, dev_num);
    if (my_class) class_destroy(my_class);
    cdev_del(&my_cdev);
    unregister_chrdev_region(dev_num, 1);
    pr_info("[BYNb] Driver unloaded\n");
}

module_init(driver_loader_builtin_init);
module_exit(driver_loader_builtin_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("DRKMUQEETT");
MODULE_DESCRIPTION("Built-in RT Driver Loader for Redmi Note 8 Pro (Begonia)");
MODULE_VERSION("5.10_A12");
