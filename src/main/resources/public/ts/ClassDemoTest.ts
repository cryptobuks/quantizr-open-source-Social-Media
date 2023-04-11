
/**
 * This class was for proving how certain aspects of inheritance work in TypeScript
 * related to Fat Arrow functions, binding, and overriding base class functions. There are only
 * a handful of files with "Demo" in their filename, and those can be removed from this project and
 * the web app will still work fine of course.
 *
 * Important rules of thumb:
 *
 * For a normal class member (no arrow function) if you are ever using it in a way that's not an 'immediate call'
 * then you need to be sure it's either binded, or a fat arrow function.
 *
 */

class Animal {
    type: string = "AnimalType";

    constructor(public name: string) {
        console.log("Constructor: Animal");
        // this.askGoForWalk = this.askGoForWalk.bind(this);
    }

    // NON-arrow is required in order to call on 'super.printName'
    printName() {
        console.log("printName(Animal): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }

    askGoForWalk = () => {
        console.log("goForWalk(Animal): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }
}

class Dog extends Animal {
    override type: string = "DogType";

    constructor(public override name: string) {
        super(name);
        console.log("Constructor: Dog");
        // this.askGoForWalk = this.askGoForWalk.bind(this);
    }

    // NON-arrow is required in order to call on 'super.printName'
    override printName() {
        super.printName();
        console.log("printName(Dog): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }

    override askGoForWalk = () => {
         // NOTE: This fails. Super will not have askGoForWalk since it was created with fat arrow.
        // super.askGoForWalk();
        console.log("goForWalk(Dog): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }
}

class Labrador extends Dog {
    override type: string = "LabradorType";

    constructor(public override name: string) {
        super(name);
        console.log("Constructor: Labrador");
        // this.askGoForWalk = this.askGoForWalk.bind(this);
    }

    override printName = () => {
        super.printName();
        console.log("printName(Labrador): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }

    override askGoForWalk = () => {
        // NOTE: This fails. Super will not have askGoForWalk since it was created with fat arrow.
        // super.askGoForWalk();
        console.log("goForWalk(Labrador): name is:" + this.name + " type=" + this.type + " this.constructor.name=" + this.constructor.name);
    }
}

export function runClassDemoTest() {
    setTimeout(() => {

        console.log("----------------- Running classDemoTest (binding override test)");

        // let startTime = S.util.perfStart();
        // let iters = 100000000;
        // for (let i = 0; i < iters; i++) {
        //  let dog = new Dog();
        // }
        // S.util.perfEnd("Test completed: iters=" + iters, startTime);

        const fido = new Labrador("Fido");
        fido.askGoForWalk();
        fido.printName();

        // console.log("Calling bound function as property of different object.");

        // /* This works and demonstrates a function being separable from what calls it when not bound */
        // let buffy = new Labrador("Buffy");
        // let askGoForWalk2 = fido.askGoForWalk;
        // (buffy as any).askGoForWalk2 = askGoForWalk2;
        // (buffy as any).askGoForWalk2(); // prints buffy even though function ref came off fido

        // buffy = new Labrador("Buffy");
        // askGoForWalk2 = fido.askGoForWalk.bind(fido);
        // (buffy as any).askGoForWalk2 = askGoForWalk2;
        // (buffy as any).askGoForWalk2(); // prints fido even though function is called on buffy (due to binding)

        console.log("----------------- end of test");
    }, 100);
}
